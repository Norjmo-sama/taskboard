package com.example.taskboardandroid

object TitleValidator {
    const val MAX_LENGTH = 80

    sealed interface Result {
        data class Valid(val title: String) : Result
        data class Invalid(val message: String) : Result
    }

    fun validate(raw: String): Result {
        val title = raw.trim()
        // Count code points so emoji count as one character, closer to Firestore's size().
        val length = title.codePointCount(0, title.length)
        return when {
            length == 0 -> Result.Invalid("Title can’t be blank.")
            length > MAX_LENGTH ->
                Result.Invalid("Title must be $MAX_LENGTH characters or fewer (currently $length).")
            else -> Result.Valid(title)
        }
    }

    fun trimmedLength(raw: String): Int = raw.trim().let { it.codePointCount(0, it.length) }
}