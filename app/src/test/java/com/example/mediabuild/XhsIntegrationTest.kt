package com.example.mediabuild

import com.example.mediabuild.parser.LinkParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class XhsIntegrationTest {

    @Test
    fun `parse xhslink short url AuiGnVDkYHt`() = runBlocking {
        val result = LinkParser.parse("http://xhslink.com/o/AuiGnVDkYHt")
        println("Success: ${result.success}")
        println("Error: ${result.error}")
        println("Items: ${result.items?.size}")
        result.items?.forEach {
            println("  Type: ${it.type}, URL: ${it.url.take(80)}")
        }
        assertTrue("应该解析成功", result.success)
        assertTrue("应该有媒体内容", result.items?.isNotEmpty() == true)
    }

    @Test
    fun `parse xhslink short url 7E2eHdEbROd`() = runBlocking {
        val result = LinkParser.parse("http://xhslink.com/o/7E2eHdEbROd")
        println("Success: ${result.success}")
        println("Error: ${result.error}")
        println("Items: ${result.items?.size}")
        result.items?.forEach {
            println("  Type: ${it.type}, URL: ${it.url.take(80)}")
        }
        assertTrue("应该解析成功", result.success)
        assertTrue("应该有媒体内容", result.items?.isNotEmpty() == true)
    }
}
