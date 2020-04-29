package redis.protocol

import java.io.InputStream

import cats._
import cats.implicits._
import cats.data._
import cats.effect.IO
import java.io.BufferedReader


trait RDReader {
    def readChar(): Char
    def readInt(): Int
    def readString(): String
}

class RDInputReader(is: BufferedReader) extends RDReader {
    def readChar(): Char = is.read().toChar 
    def readInt(): Int = is.read()
    def readString(): String = is.readLine()

}

class Parser(implicit reader: RDReader) {
    def parseSimpleString() = reader.readString()

    def parseErrors() = reader.readString()

    def parseInteger() = reader.readInt()

    def parse() = {
        var ch = reader.readChar()
        ch match {
            case '+' => RDSimpleString(parseSimpleString())
            case '-' => RDError(parseErrors())
            case ':' => RDInteger(parseInteger())
        }
    }

}