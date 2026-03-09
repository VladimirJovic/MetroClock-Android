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

@Composable
fun ProfileScreen(authService: AuthService) {
    val user by authService.currentUser.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080810))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Profile", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)

            user?.let { u ->
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0xFF1E1E30), RoundedCornerShape(24.dp))
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${u.firstName.firstOrNull() ?: ""}${u.lastName.firstOrNull() ?: ""}",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF5B8EF5)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F0F1A), RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ProfileRow(label = "Name", value = u.fullName)
                    Divider(color = Color(0xFF2A2A40))
                    ProfileRow(label = "Email", value = u.email)
                    Divider(color = Color(0xFF2A2A40))
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E30))
                ) {
                    Text("Log Out", fontSize = 16.sp, color = Color(0xFFF55252), fontWeight = FontWeight.SemiBold)
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
        Text(label, fontSize = 13.sp, color = Color(0xFF8888AA))
        Text(value, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
    }
}