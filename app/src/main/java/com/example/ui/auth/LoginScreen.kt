package com.example.ui.auth

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UserRepository
import com.example.models.Role
import com.example.models.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(
    onLoginSuccess: (Role) -> Unit
) {
    var hrmsId by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var isOtpSent by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var currentUser by remember { mutableStateOf<User?>(null) }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val repository = remember { UserRepository() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ROICMS",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 2.sp
                    )
                )
                Text(
                    text = "Railway Officer Inspection & Compliance Management System",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
                )

                if (!isOtpSent) {
                    OutlinedTextField(
                        value = hrmsId,
                        onValueChange = { hrmsId = it.trim().uppercase() },
                        label = { Text("HRMS ID or Mobile No.") },
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = "HRMS ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            if (hrmsId.isNotBlank()) {
                                isLoading = true
                                scope.launch(Dispatchers.IO) {
                                    val user = repository.getUserByHrmsOrMobile(hrmsId)
                                    withContext(Dispatchers.Main) {
                                        isLoading = false
                                        if (user != null) {
                                            currentUser = user
                                            isOtpSent = true
                                            Toast.makeText(context, "OTP sent successfully.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            // Handle case where Admin logs in for the first time without DB entry
                                            if (hrmsId == "ADMIN") {
                                                currentUser = User(name = "Super Admin", role = Role.ADMIN, hrmsId = "ADMIN")
                                                isOtpSent = true
                                                Toast.makeText(context, "OTP sent successfully.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "User not found. Contact Admin.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Text("Send OTP", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Text(
                        text = "OTP sent to ${currentUser?.mobile?.ifEmpty { currentUser?.hrmsId }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    OutlinedTextField(
                        value = otp,
                        onValueChange = { otp = it },
                        label = { Text("Enter 6-digit OTP") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "OTP") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            if (otp == "123456") {
                                isLoading = true
                                currentUser?.let { user ->
                                    com.example.data.SessionManager.login(user)
                                    onLoginSuccess(user.role)
                                }
                                isLoading = false
                            } else {
                                Toast.makeText(context, "Invalid OTP", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Text("Verify & Login", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    TextButton(
                        onClick = { isOtpSent = false },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Change HRMS ID / Mobile")
                    }
                }
            }
        }
    }
}
