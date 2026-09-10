package com.gsbtechnologies.lotto642modeltracker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Navy=Color(0xFF0B1F3A)
private val Navy2=Color(0xFF153A63)
private val Gold=Color(0xFFD4A72C)
private val Light=lightColorScheme(primary=Navy,secondary=Gold,tertiary=Navy2,surface=Color(0xFFF7F9FC),background=Color(0xFFF2F5F9))
private val Dark=darkColorScheme(primary=Color(0xFF9CC8FF),secondary=Color(0xFFE8C66A),surface=Color(0xFF122236),background=Color(0xFF081321))

@Composable
fun LottoTheme(content:@Composable()->Unit){
    MaterialTheme(colorScheme=if(isSystemInDarkTheme())Dark else Light,typography=Typography(),content=content)
}
