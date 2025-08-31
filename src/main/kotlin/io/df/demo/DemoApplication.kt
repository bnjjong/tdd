package io.df.demo



class DemoApplication


fun main() {
    println("Hello, Kotlin World!")
    println("This is a Kotlin 2.x demo project with Gradle 8.5+")
    
    val greeting = createGreeting("Kotlin Developer")
    println(greeting)
}

fun createGreeting(name: String): String {
    return "Welcome, $name! 🎉"
}