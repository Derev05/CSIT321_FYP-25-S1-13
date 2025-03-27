package com.example.kotlinbasics

fun main() {
    /*println("Please enter a number:")
    var inputString = readln()
    var inputNumber = inputString.toInt()
    val multiplier = 5
    println("Result of operation is: " + inputNumber * multiplier)*/
    println("Rock,Paper,Scissors Please enter your choice!: ")
    var playerChoice = readln()
    var finalChoice = playerChoice.lowercase()
    while (finalChoice != playerChoice && finalChoice != "Rock" && finalChoice != "Paper" && finalChoice != "Sicssors"){
        println("Please re-enter a valid choice: ")
        playerChoice = readln()

    }
    println(playerChoice)

}
