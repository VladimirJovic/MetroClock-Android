package com.echoproduction.metroclock.services

import com.echoproduction.metroclock.models.MCUser
import com.echoproduction.metroclock.models.UserRole
import com.google.firebase.auth.FirebaseAuth
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
                fetchUser(uid)
            }
            .addOnFailureListener { e ->
                _isLoading.value = false
                _errorMessage.value = "Invalid email or password"
            }
    }

    private fun fetchUser(uid: String) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                _isLoading.value = false
                val data = doc.data ?: return@addOnSuccessListener
                val roleStr = (data["role"] as? String)?.uppercase() ?: "EMPLOYEE"
                _currentUser.value = MCUser(
                    id = doc.id,
                    email = data["email"] as? String ?: "",
                    firstName = data["firstName"] as? String ?: "",
                    lastName = data["lastName"] as? String ?: "",
                    role = UserRole.valueOf(roleStr),
                    workspaceId = data["workspaceId"] as? String ?: "",
                    managerId = data["managerId"] as? String,
                    profileImageURL = data["profileImageURL"] as? String,
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
            }
            .addOnFailureListener {
                _isLoading.value = false
            }
    }

    fun logout() {
        auth.signOut()
        _currentUser.value = null
    }

    fun checkCurrentUser() {
        val uid = auth.currentUser?.uid ?: return
        fetchUser(uid)
    }
}