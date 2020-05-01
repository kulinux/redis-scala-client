package redis.protocol

import java.io.InputStream

import cats._
import cats.implicits._
import cats.data._
import cats.effect.IO
import java.io.BufferedReader
import java.io.ByteArrayOutputStream


trait RDReader {
    def readChar(): Char
    def readInt(): Int
    def readString(): String
    def read(length: Int): Array[Byte]
    def skip(length: Int)
}

class RDInputReader(is: InputStream) extends RDReader {
    def readChar(): Char = is.read().toChar 
    def readInt(): Int = readString().toInt

    def skip(length: Int): Unit = is.skip(length)
    def read(length: Int): Array[Byte] = {
        val avail = is.available()
        if(avail >= length) {
            val arr = new Array[Byte](length)
            is.read(arr)
            return arr
        } else {
            val bos = new ByteArrayOutputStream()
            var read = -1
            var count = 0
            read = is.read()
            while( read  >= 0 && count < length)
            {
                count = count + 1
                bos.write(read)
                read = is.read()
            }
            return bos.toByteArray()
        }
    }

    def readString(): String = {
        val bos = new ByteArrayOutputStream()
        var encountR = false
        var read = is.read()
        while( read >= 0 ) {
            encountR = read == '\r'
            if(read == '\r')
            {
                is.mark(2)
                if(is.read() == '\n') {
                    return new String(bos.toByteArray())
                } else {
                    is.reset()
                }
            }
            bos.write(read)
            read = is.read()
        }
        throw new RuntimeException("Error, \\r\\n not found")
    }

}

class Parser(reader: RDReader) {
    def parseSimpleString() = reader.readString()

    def parseErrors() = reader.readString()

    def parseInteger() = reader.readInt()

    def parseBulkString(): Array[Byte] = {
        val length = reader.readInt()
        reader.read(length)
    } 

    def parse(): RDMessage = {
        var ch = reader.readChar()
        ch match {
            case '+' => RDSimpleString(parseSimpleString())
            case '-' => RDError(parseErrors())
            case ':' => RDInteger(parseInteger())
            case '$' => RDBulkString(parseBulkString())
        }
    }

}