package com.carecompanion.presentation.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carecompanion.presentation.viewmodels.LoginViewModel

// ─────────────────────────────────────────────────────────────────────────────
// Entry point — routes to setup or login based on state
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) onLoginSuccess()
    }

    when {
        state.isLoading -> LoadingScreen()
        state.needsSetup -> FirstTimeSetupScreen(viewModel = viewModel)
        else -> SignInScreen(viewModel = viewModel)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Loading splash
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LoadingScreen() {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF1565C0), Color(0xFF0D47A1)))
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text("CareCompanion", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sign-in form
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SignInScreen(viewModel: LoginViewModel) {
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    var pinVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1565C0), Color(0xFF1976D2))))
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(60.dp))

        // ── App identity ─────────────────────────────────────────────────────
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f))
        ) {
            Icon(Icons.Default.LocalHospital, contentDescription = null,
                tint = Color.White, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("CareCompanion", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text("HIV Patient Management System", style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.80f))
        Spacer(Modifier.height(40.dp))

        // ── Sign-in card ─────────────────────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Sign In", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Enter your credentials to continue", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                OutlinedTextField(
                    value = state.loginUsername,
                    onValueChange = viewModel::onLoginUsernameChanged,
                    label = { Text("Username") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    isError = state.loginError != null
                )

                OutlinedTextField(
                    value = state.loginPin,
                    onValueChange = viewModel::onLoginPinChanged,
                    label = { Text("PIN (4–6 digits)") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { pinVisible = !pinVisible }) {
                            Icon(
                                if (pinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (pinVisible) "Hide PIN" else "Show PIN"
                            )
                        }
                    },
                    visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); viewModel.login() }),
                    isError = state.loginError != null
                )

                AnimatedVisibility(visible = state.loginError != null) {
                    Text(state.loginError ?: "", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    onClick = viewModel::login,
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sign In", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        Text(
            "Contact your facility administrator if you cannot log in.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.70f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// First-time setup form (creates the initial Admin account)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun FirstTimeSetupScreen(viewModel: LoginViewModel) {
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    var pinVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1B5E20), Color(0xFF2E7D32))))
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(72.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f))
        ) {
            Icon(Icons.Default.AdminPanelSettings, contentDescription = null,
                tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("Initial Setup", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, color = Color.White)
        Text("Create the first administrator account", style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.80f))
        Spacer(Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

                // Info banner
                Surface(
                    color = Color(0xFFE8F5E9), shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null,
                            tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                        Text(
                            "This account will have full Administrator access. " +
                            "Additional staff accounts can be created from Settings after login. " +
                            "Accounts can also sync from WINCO once user management is configured.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF1B5E20), lineHeight = 18.sp
                        )
                    }
                }

                OutlinedTextField(
                    value = state.setupFullName,
                    onValueChange = viewModel::onSetupFullNameChanged,
                    label = { Text("Full Name") },
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    isError = state.setupError?.contains("name", ignoreCase = true) == true
                )

                OutlinedTextField(
                    value = state.setupUsername,
                    onValueChange = viewModel::onSetupUsernameChanged,
                    label = { Text("Username (no spaces)") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    isError = state.setupError?.contains("username", ignoreCase = true) == true
                )

                OutlinedTextField(
                    value = state.setupPin,
                    onValueChange = viewModel::onSetupPinChanged,
                    label = { Text("PIN (4–6 digits)") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { pinVisible = !pinVisible }) {
                            Icon(if (pinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null)
                        }
                    },
                    visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    isError = state.setupError?.contains("PIN", ignoreCase = true) == true ||
                              state.setupError?.contains("pin", ignoreCase = true) == true
                )

                OutlinedTextField(
                    value = state.setupConfirmPin,
                    onValueChange = viewModel::onSetupConfirmPinChanged,
                    label = { Text("Confirm PIN") },
                    leadingIcon = { Icon(Icons.Default.LockReset, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); viewModel.createAdminAccount() }),
                    isError = state.setupError?.contains("match", ignoreCase = true) == true
                )

                AnimatedVisibility(visible = state.setupError != null) {
                    Text(state.setupError ?: "", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    onClick = viewModel::createAdminAccount,
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.HowToReg, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Create Account", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
