package com.scr01.mod

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Shared by frequency, NAT and APN cards: caller supplies the same outer SectionPadding. */
@Composable
internal fun ActionCardHeader(title:String,description:String,actions:@Composable ()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(AppUi.SpaceMd)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(AppUi.SpaceXs)) {
            Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Medium,
                maxLines=1,overflow=TextOverflow.Ellipsis)
            if(description.isNotBlank()) Text(description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        Box(contentAlignment=Alignment.CenterEnd) { actions() }
    }
}
