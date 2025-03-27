package com.example.kotlinbasics
import com.google.firebase.auth.FirebaseAuth
class AuthHelper {

    class AuthHelper {
        private val auth: FirebaseAuth = FirebaseAuth.getInstance()

        fun registerUser(email: String, password: String, onResult: (Boolean, String) -> Unit) {
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        onResult(true, "Registration Successful!")
                    } else {
                        onResult(false, task.exception?.message ?: "Registration Failed!")
                    }
                }
        }
    }
}