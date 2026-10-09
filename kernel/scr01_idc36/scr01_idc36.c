#include <linux/init.h>
#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/mm.h>
#include <linux/string.h>
#include <linux/stop_machine.h>
#include <linux/vmalloc.h>
#include <asm/cacheflush.h>
#include <asm/page.h>
#include <asm/pgtable-prot.h>

#define DRV_NAME "scr01_idc36"
#define WRAPPER_ADDR  0xffffff80080ac890UL
#define CALLSITE_ADDR 0xffffff800884a530UL
#define SCR01_KIMAGE_VA_BASE   0xffffff8008000000UL
#define SCR01_KIMAGE_PHYS_BASE 0x40000000UL

static const u8 wrapper_original[32];
static const u8 wrapper_patch[32] = {
	0xa8, 0x42, 0x45, 0x39, 0x08, 0x91, 0x00, 0x51,
	0x1f, 0x31, 0x00, 0x71, 0x88, 0x00, 0x00, 0x54,
	0x1f, 0x05, 0x00, 0x72, 0x41, 0x00, 0x00, 0x54,
	0xc0, 0x03, 0x5f, 0xd6, 0x68, 0x75, 0x1e, 0x14,
};
static const u8 callsite_original[4] = { 0x47, 0xfe, 0xff, 0x97 };
static const u8 callsite_patch[4] = { 0xd8, 0x88, 0xe1, 0x97 };

struct text_write {
	unsigned long target;
	void *mapping;
	void *alias;
	const u8 *bytes;
	size_t size;
};

static bool patch_active;

/* PAGE_KERNEL with PTE_NG selected unconditionally, avoiding the runtime
 * arm64_kernel_unmapped_at_el0() capability dependency.  A non-global vmap
 * alias is valid regardless of whether KPTI is active on a given CPU.
 */
#define SCR01_ALIAS_PROT __pgprot(_PROT_DEFAULT | PTE_NG | PTE_PXN | \
				 PTE_UXN | PTE_WRITE | PTE_ATTRINDX(MT_NORMAL))

/* Match the physical-page vmap mechanism already used successfully by
 * ksu_glue.  Its recorded sct_va/sct_phys pair resolves this exact build's
 * kernel Image mapping to VA 0xffffff8008000000 -> PA 0x40000000.
 *
 * CONFIG_SPARSEMEM_VMEMMAP lays out one struct page per PFN starting at
 * VMEMMAP_START.  Expressing the page from the verified physical offset
 * avoids treating the separate kernel Image VA as a linear-map address.
 */
static struct page *scr01_image_page(unsigned long target)
{
	unsigned long phys = SCR01_KIMAGE_PHYS_BASE +
		(target - SCR01_KIMAGE_VA_BASE);
	unsigned long pfn_offset =
		(phys - SCR01_KIMAGE_PHYS_BASE) >> PAGE_SHIFT;

	return (struct page *)(VMEMMAP_START +
		pfn_offset * sizeof(struct page));
}

static int text_write_stopped(void *arg)
{
	struct text_write *op = arg;

	memcpy(op->alias, op->bytes, op->size);
	asm volatile("dmb ishst" : : : "memory");
	flush_icache_range((unsigned long)op->alias,
			   (unsigned long)op->alias + op->size);
	flush_icache_range(op->target, op->target + op->size);
	return 0;
}

static int write_text_checked(unsigned long target, const u8 *expected,
			      const u8 *replacement, size_t size)
{
	struct page *pages[1];
	struct text_write op;
	unsigned long page_addr = target & PAGE_MASK;
	unsigned long offset = target & ~PAGE_MASK;
	int ret;

	if (!size || offset + size > PAGE_SIZE)
		return -EINVAL;
	if (memcmp((const void *)target, expected, size)) {
		pr_err(DRV_NAME ": byte gate failed at %px\n", (void *)target);
		return -ESTALE;
	}

	pages[0] = scr01_image_page(page_addr);
	op.mapping = vmap(pages, 1, VM_MAP, SCR01_ALIAS_PROT);
	if (!op.mapping)
		return -ENOMEM;

	op.target = target;
	op.alias = (u8 *)op.mapping + offset;
	op.bytes = replacement;
	op.size = size;

	ret = stop_machine(text_write_stopped, &op, NULL);
	if (!ret && memcmp((const void *)target, replacement, size))
		ret = -EIO;
	vunmap(op.mapping);
	return ret;
}

static int activate_patch(void)
{
	int ret;

	if (memcmp((const void *)WRAPPER_ADDR, wrapper_original,
		   sizeof(wrapper_original))) {
		pr_err(DRV_NAME ": wrapper cave is not zero-filled\n");
		return -ESTALE;
	}
	if (memcmp((const void *)CALLSITE_ADDR, callsite_original,
		   sizeof(callsite_original))) {
		pr_err(DRV_NAME ": callsite original bytes do not match\n");
		return -ESTALE;
	}

	ret = write_text_checked(WRAPPER_ADDR, wrapper_original, wrapper_patch,
				 sizeof(wrapper_patch));
	if (ret)
		return ret;

	ret = write_text_checked(CALLSITE_ADDR, callsite_original, callsite_patch,
				 sizeof(callsite_patch));
	if (ret) {
		int undo = write_text_checked(WRAPPER_ADDR, wrapper_patch,
					      wrapper_original,
					      sizeof(wrapper_original));
		if (undo)
			pr_emerg(DRV_NAME ": wrapper rollback failed: %d\n", undo);
		return ret;
	}

	patch_active = true;
	pr_info(DRV_NAME ": patch active wrapper=%32phN callsite=%4phN\n",
		(const void *)WRAPPER_ADDR, (const void *)CALLSITE_ADDR);
	return 0;
}

static void deactivate_patch(void)
{
	int ret;

	if (!patch_active)
		return;

	ret = write_text_checked(CALLSITE_ADDR, callsite_patch,
				 callsite_original, sizeof(callsite_original));
	if (ret) {
		pr_emerg(DRV_NAME ": callsite rollback failed: %d; wrapper retained\n",
			 ret);
		return;
	}

	ret = write_text_checked(WRAPPER_ADDR, wrapper_patch, wrapper_original,
				 sizeof(wrapper_original));
	if (ret) {
		pr_emerg(DRV_NAME ": wrapper cleanup failed: %d\n", ret);
		return;
	}

	patch_active = false;
	pr_info(DRV_NAME ": patch inactive wrapper=%32phN callsite=%4phN\n",
		(const void *)WRAPPER_ADDR, (const void *)CALLSITE_ADDR);
}

static int __init scr01_idc36_init(void)
{
	return activate_patch();
}

static void __exit scr01_idc36_exit(void)
{
	deactivate_patch();
}

module_init(scr01_idc36_init);
module_exit(scr01_idc36_exit);

MODULE_LICENSE("GPL");
MODULE_AUTHOR("SCR01 local audit");
MODULE_DESCRIPTION("Temporary SCR01 IDC SAP 36-48 CSA suppression patch");
