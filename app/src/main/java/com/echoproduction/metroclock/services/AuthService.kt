package com.echoproduction.metroclock.services

import com.echoproduction.metroclock.models.MCUser
import com.echoproduction.metroclock.models.UserRole
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AuthService {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _currentUser = MutableStateFlow<MCUser?>(null)
    val currentUser: StateFlow<MCUser?> = _currentUser

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun login(email: String, password: String) {
        _isLoading.value = true
        _errorMessage.value = null
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: return@addOnSuccessListener
                fetchUser(uid, result.user?.email)
            }
            .addOnFailureListener {
                _isLoading.value = false
                _errorMessage.value = "Invalid email or password"
            }
    }

    fun handleGoogleSignInResult(account: GoogleSignInAccount) {
        _isLoading.value = true
        _errorMessage.value = null
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: return@addOnSuccessListener
                fetchUser(uid, result.user?.email)
            }
            .addOnFailureListener { e ->
                _isLoading.value = false
                _errorMessage.value = e.message ?: "Google Sign-In failed"
            }
    }

    private fun fetchUser(uid: String, email: String? = null) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val data = doc.data
                if (data != null && data.isNotEmpty()) {
                    parseAndSetUser(data, doc.id)
                } else if (email != null) {
                    // Google UID doesn't match any doc — look up by email (first-time Google login)
                    fetchUserByEmail(email)
                } else {
                    _isLoading.value = false
                    _errorMessage.value = "User not found"
                }
            }
            .addOnFailureListener { e ->
                _isLoading.value = false
                if (email != null) {
                    fetchUserByEmail(email)
                } else {
                    _errorMessage.value = e.message
                }
            }
    }

    private fun fetchUserByEmail(email: String) {
        db.collection("users").whereEqualTo("email", email).limit(1).get()
            .addOnSuccessListener { snapshot ->
                _isLoading.value = false
                val doc = snapshot.documents.firstOrNull()
                if (doc == null) {
                    auth.signOut()
                    _errorMessage.value = "No account found for this email. Contact your admin."
                    return@addOnSuccessListener
                }
                val data = doc.data ?: return@addOnSuccessListener
                parseAndSetUser(data, doc.id)
            }
            .addOnFailureListener { e ->
                _isLoading.value = false
                _errorMessage.value = e.message
            }
    }

    private fun parseAndSetUser(data: Map<String, Any>, docId: String) {
        _isLoading.value = false
        val roleStr = (data["role"] as? String)?.uppercase() ?: "EMPLOYEE"
        val user = MCUser(
            id = (data["id"] as? String) ?: docId,
            email = data["email"] as? String ?: "",
            firstName = data["firstName"] as? String ?: "",
            lastName = data["lastName"] as? String ?: "",
            role = try { UserRole.valueOf(roleStr) } catch (e: Exception) { UserRole.EMPLOYEE },
            workspaceId = data["workspaceId"] as? String ?: "",
            managerId = data["managerId"] as? String,
            isActive = data["isActive"] as? Boolean ?: true,
            workDays = (data["workDays"] as? List<*>)?.mapNotNull { (it as? Long)?.toInt() },
            dailyHours = (data["dailyHours"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val value = (v as? Double) ?: (v as? Long)?.toDouble() ?: return@mapNotNull null
                key to value
            }?.toMap(),
            hourlyRate = (data["hourlyRate"] as? Double) ?: (data["hourlyRate"] as? Long)?.toDouble(),
            currency = data["currency"] as? String,
            overtimeMultiplier = (data["overtimeMultiplier"] as? Double) ?: (data["overtimeMultiplier"] as? Long)?.toDouble()
        )
        if (!user.isActive) {
            auth.signOut()
            _errorMessage.value = "Your account has been deactivated. Contact your administrator."
            return
        }
        _currentUser.value = user
    }

    fun logout() {
        auth.signOut()
        _currentUser.value = null
    }

    fun checkCurrentUser() {
        val firebaseUser = auth.currentUser ?: return
        fetchUser(firebaseUser.uid, firebaseUser.email)
    }
}
