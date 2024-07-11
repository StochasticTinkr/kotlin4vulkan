package com.stochastictinkr.vulkan.playground.generator

import org.w3c.dom.Document
import org.w3c.dom.Node
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Load an XML file into a [Document].
 */
fun loadXml(file: Path): Document {
    return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file.toFile())
}

/**
 * The sequence of child nodes of this node.
 */
val Node.children: Sequence<Node> get() = generateSequence(firstChild) { it.nextSibling }

/**
 * The sequence of child nodes of this node with the given name.
 */
fun Node.children(name: String): Sequence<Node> = children.filter { it.nodeName == name }

/**
 * Check if this node has an attribute with the given name.
 */
fun Node.hasAttribute(name: String) = attributes.getNamedItem(name) != null

/**
 * Get the value of an attribute with the given name, or null if it does not exist.
 */
fun Node.attribute(name: String) = attributes.getNamedItem(name)?.textContent
