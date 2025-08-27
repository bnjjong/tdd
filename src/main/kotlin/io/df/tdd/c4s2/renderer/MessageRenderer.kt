package io.df.tdd.c4s2.renderer

interface HtmlRenderer {
    fun render(message: String): String
}

class MessageRenderer : HtmlRenderer {
    private var tagType: String = "div"

    override fun render(message: String): String {
        return "<$tagType>$message</$tagType>"
    }

    fun setTagType(tag: String) {
        this.tagType = tag
    }
}
