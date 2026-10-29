package com.example.taskboardandroid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

data class TaskItem(
    val id: String,
    val title: String,
    val completed: Boolean,
    val createdAt: Date?,
    val createdBy: String,
)

sealed interface ListState {
    data object Loading : ListState
    data class Error(val message: String) : ListState
    data class Loaded(val tasks: List<TaskItem>) : ListState
}

data class UiState(
    val authChecked: Boolean = false,
    val uid: String? = null,
    val listState: ListState = ListState.Loading,
    val draft: String = "",
    val saving: Boolean = false,
    val formError: String? = null,
    val actionError: String? = null,
    val authError: String? = null,
)

class TaskViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var listener: ListenerRegistration? = null

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val authListener = FirebaseAuth.AuthStateListener { a ->
        val uid = a.currentUser?.uid
        _state.update { it.copy(authChecked = true, uid = uid) }
        if (uid != null) startListening() else stopListening()
    }

    init {
        auth.addAuthStateListener(authListener)
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authListener)
        listener?.remove()
    }

    // ---- Auth ----
    fun signIn() = viewModelScope.launch {
        _state.update { it.copy(authError = null) }
        try {
            auth.signInAnonymously().await()
        } catch (e: Exception) {
            _state.update { it.copy(authError = friendly(e)) }
        }
    }

    fun signOut() = auth.signOut()

    // ---- Firestore ----
    private fun startListening() {
        listener?.remove()
        _state.update { it.copy(listState = ListState.Loading) }
        listener = db.collection("tasks")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    _state.update { it.copy(listState = ListState.Error(friendly(err))) }
                    return@addSnapshotListener
                }
                val tasks = snap?.documents.orEmpty().map { d ->
                    TaskItem(
                        id = d.id,
                        title = d.getString("title").orEmpty(),
                        completed = d.getBoolean("completed") ?: false,
                        // ESTIMATE gives our own pending server timestamps a local value.
                        createdAt = d.getTimestamp(
                            "createdAt",
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                        )?.toDate(),
                        createdBy = d.getString("createdBy").orEmpty(),
                    )
                }
                _state.update { it.copy(listState = ListState.Loaded(tasks)) }
            }
    }

    private fun stopListening() {
        listener?.remove()
        listener = null
        _state.update { it.copy(listState = ListState.Loading) }
    }

    fun onDraftChange(value: String) =
        _state.update { it.copy(draft = value, formError = null) }

    fun addTask() {
        val s = _state.value
        val uid = s.uid ?: return
        if (s.saving) return
        when (val v = TitleValidator.validate(s.draft)) {
            is TitleValidator.Result.Invalid ->
                _state.update { it.copy(formError = v.message) }
            is TitleValidator.Result.Valid -> viewModelScope.launch {
                _state.update { it.copy(saving = true, formError = null) }
                try {
                    db.collection("tasks").add(
                        mapOf(
                            "title" to v.title,
                            "completed" to false,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "createdBy" to uid,
                        )
                    ).await()
                    _state.update { it.copy(draft = "", saving = false) }
                } catch (e: Exception) {
                    _state.update { it.copy(saving = false, formError = friendly(e)) }
                }
            }
        }
    }

    fun toggle(task: TaskItem) = viewModelScope.launch {
        _state.update { it.copy(actionError = null) }
        try {
            db.collection("tasks").document(task.id)
                .update("completed", !task.completed).await()
        } catch (e: Exception) {
            _state.update { it.copy(actionError = friendly(e)) }
        }
    }

    private fun friendly(e: Exception): String = when {
        e is FirebaseFirestoreException &&
                e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Permission denied by Firestore security rules."
        e is FirebaseFirestoreException &&
                e.code == FirebaseFirestoreException.Code.UNAVAILABLE ->
            "You appear to be offline. Changes will sync when you reconnect."
        e is FirebaseAuthException && e.errorCode == "ERROR_OPERATION_NOT_ALLOWED" ->
            "Anonymous sign-in is disabled in the Firebase console."
        else -> e.localizedMessage ?: "Something went wrong."
    }
}
