package com.echoproduction.metroclock.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoproduction.metroclock.R
import com.echoproduction.metroclock.services.AuthService
import com.echoproduction.metroclock.ui.theme.LocalMcColors
import com.echoproduction.metroclock.ui.theme.McOrange

@Composable
fun LoginScreen(
    authService: AuthService,
    onGoogleSignIn: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val isLoading by authService.isLoading.collectAsState()
    val errorMessage by authService.errorMessage.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalMcColors.current.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Spacer(modifier = Modifier.height(72.dp))

            // ── App logo ──────────────────────────────────────────────────
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "MetroClock",
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(22.dp))
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Wordmark ──────────────────────────────────────────────────
            Text(
                text = "MetroClock",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "by Echo Production",
                fontSize = 13.sp,
                color = LocalMcColors.current.textSecondary,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(40.dp))

            // ── Form card ─────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LocalMcColors.current.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, LocalMcColors.current.border, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Email label
                Text(
                    "EMAIL",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = LocalMcColors.current.textTertiary
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = { Text("you@company.com", color = LocalMcColors.current.textTertiary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = McOrange,
                        unfocusedBorderColor = LocalMcColors.current.border,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = McOrange,
                        unfocusedContainerColor = LocalMcColors.current.background,
                        focusedContainerColor = LocalMcColors.current.background
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                // Password label
                Text(
                    "PASSWORD",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = LocalMcColors.current.textTertiary
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text("••••••••", color = LocalMcColors.current.textTertiary) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = McOrange,
                        unfocusedBorderColor = LocalMcColors.current.border,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = McOrange,
                        unfocusedContainerColor = LocalMcColors.current.background,
                        focusedContainerColor = LocalMcColors.current.background
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                // Error
                if (errorMessage != null) {
                    Text(
                        text = "⚠ $errorMessage",
                        color = Color(0xFFF55252),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Sign In button
                Button(
                    onClick = { authService.login(email, password) },
                    enabled = email.isNotEmpty() && password.isNotEmpty() && !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = McOrange,
                        disabledContainerColor = LocalMcColors.current.border
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            "SIGN IN",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Divider ───────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = LocalMcColors.current.border)
                Text(
                    text = "  or  ",
                    fontSize = 12.sp,
                    color = LocalMcColors.current.textTertiary
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = LocalMcColors.current.border)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Google Sign-In ────────────────────────────────────────────
            OutlinedButton(
                onClick = onGoogleSignIn,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = LocalMcColors.current.surface,
                    contentColor = Color.White
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, LocalMcColors.current.border)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_google),
                        contentDescription = "Google",
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Sign in with Google",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = LocalMcColors.current.text
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
