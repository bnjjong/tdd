package io.df.tdd.service

class EmailService {
    fun sendEmail(to: String, message: String): Boolean {
        // 실제 이메일 전송 로직이 들어갈 자리입니다.
        // 현재는 단순히 true를 반환합니다.
        return true
    }
}

class NotificationService(private val emailService: EmailService) {
    fun notifyUser(userId: String, message: String): Boolean {
        val userEmail = getUserEmail(userId)
        return emailService.sendEmail(userEmail, message)
    }

    private fun getUserEmail(userId: String): String {
        return "$userId@example.com"
    }
}
