package com.echoproduction.metroclock.ui.manager

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoproduction.metroclock.models.UserRole
import com.echoproduction.metroclock.services.AuthService
import com.echoproduction.metroclock.ui.theme.LocalMcColors
import com.echoproduction.metroclock.ui.theme.McOrange

@Composable
fun ProfileScreen(authService: AuthService) {
    val user by authService.currentUser.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalMcColors.current.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Profile",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = LocalMcColors.current.text
            )

            user?.let { u ->
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(McOrange.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${u.firstName.firstOrNull() ?: ""}${u.lastName.firstOrNull() ?: ""}",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = McOrange
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LocalMcColors.current.surface, RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ProfileRow(label = "Name", value = u.fullName)
                    HorizontalDivider(color = LocalMcColors.current.border)
                    ProfileRow(label = "Email", value = u.email)
                    HorizontalDivider(color = LocalMcColors.current.border)
                    ProfileRow(
                        label = "Role",
                        value = when (u.role) {
                            UserRole.ADMIN -> "Admin"
                            UserRole.MANAGER -> "Manager"
                            UserRole.EMPLOYEE -> "Employee"
                        }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = { authService.logout() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF55252).copy(alpha = 0.12f)
                    )
                ) {
                    Text(
                        "LOG OUT",
                        fontSize = 14.sp,
                        color = Color(0xFFF55252),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = LocalMcColors.current.textSecondary)
        Text(value, fontSize = 13.sp, color = LocalMcColors.current.text, fontWeight = FontWeight.Medium)
    }
}
