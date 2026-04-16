package com.echoproduction.metroclock.ui.manager

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.echoproduction.metroclock.ui.theme.McRed

@Composable
fun ProfileScreen(authService: AuthService) {
    val user by authService.currentUser.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalMcColors.current.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Scrollable content ─────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    "Profile",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalMcColors.current.text,
                    modifier = Modifier.padding(top = 20.dp, start = 4.dp)
                )

                user?.let { u ->
                    val roleColor = when (u.role) {
                        UserRole.ADMIN    -> Color(0xFFA78BFA)
                        UserRole.MANAGER  -> McOrange
                        UserRole.EMPLOYEE -> Color(0xFF2DD47E)
                    }
                    val roleLabel = when (u.role) {
                        UserRole.ADMIN    -> "Admin"
                        UserRole.MANAGER  -> "Manager"
                        UserRole.EMPLOYEE -> "Employee"
                    }

                    // ── Avatar + name card ─────────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(LocalMcColors.current.surface, RoundedCornerShape(16.dp))
                            .border(1.dp, LocalMcColors.current.border, RoundedCornerShape(16.dp))
                            .padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Avatar circle
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(roleColor.copy(alpha = 0.14f), CircleShape)
                                .border(1.5.dp, roleColor.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${u.firstName.firstOrNull() ?: ""}${u.lastName.firstOrNull() ?: ""}",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = roleColor
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(u.fullName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LocalMcColors.current.text)
                            // Role badge — ALL CAPS
                            Text(
                                text = roleLabel.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                color = roleColor,
                                modifier = Modifier
                                    .background(roleColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    // ── Account info card ──────────────────────────────────────────
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "ACCOUNT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            color = LocalMcColors.current.textTertiary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(LocalMcColors.current.surface, RoundedCornerShape(12.dp))
                                .border(1.dp, LocalMcColors.current.border, RoundedCornerShape(12.dp))
                        ) {
                            ProfileRow("First Name", u.firstName)
                            HorizontalDivider(color = LocalMcColors.current.border, modifier = Modifier.padding(horizontal = 16.dp))
                            ProfileRow("Last Name", u.lastName)
                            HorizontalDivider(color = LocalMcColors.current.border, modifier = Modifier.padding(horizontal = 16.dp))
                            ProfileRow("Email", u.email)
                            HorizontalDivider(color = LocalMcColors.current.border, modifier = Modifier.padding(horizontal = 16.dp))
                            ProfileRow("Role", roleLabel)
                        }
                    }
                }
            }

            // ── Logout button pinned at bottom ─────────────────────────────────
            Column {
                HorizontalDivider(color = LocalMcColors.current.border)
                Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Button(
                        onClick = { authService.logout() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = McRed.copy(alpha = 0.10f)
                        )
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("↪", fontSize = 16.sp, color = McRed)
                            Text(
                                "LOG OUT",
                                fontSize = 14.sp,
                                color = McRed,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = LocalMcColors.current.textSecondary)
        Text(value, fontSize = 13.sp, color = LocalMcColors.current.text, fontWeight = FontWeight.Medium)
    }
}
