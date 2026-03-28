package com.echoproduction.metroclock.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoproduction.metroclock.services.AuthService
import com.echoproduction.metroclock.ui.theme.LocalMcColors
import com.echoproduction.metroclock.ui.theme.McOrange

@Composable
fun LoginScreen(authService: AuthService) {
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
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "MetroClock",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = McOrange,
                letterSpacing = 2.sp
            )
            Text(
                text = "by Echo Production",
                fontSize = 13.sp,
                color = LocalMcColors.current.textSecondary,
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email", color = LocalMcColors.current.textSecondary) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = McOrange,
                    unfocusedBorderColor = LocalMcColors.current.border,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = McOrange,
                    unfocusedContainerColor = LocalMcColors.current.surface,
                    focusedContainerColor = LocalMcColors.current.surface
                )
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password", color = LocalMcColors.current.textSecondary) },
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
                    unfocusedContainerColor = LocalMcColors.current.surface,
                    focusedContainerColor = LocalMcColors.current.surface
                )
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = Color(0xFFF55252),
                    fontSize = 13.sp
                )
            }

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
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
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
    }
}
