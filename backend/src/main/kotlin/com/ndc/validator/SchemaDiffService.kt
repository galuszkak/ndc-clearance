package com.ndc.validator

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList

import kotlinx.serialization.Serializable

@Serializable
data class DiffItem(
    val path: String,
    val type: DiffType,
    val description: String,
    val oldValue: String? = null,
    val newValue: String? = null
)

enum class DiffType {
    ADDED, REMOVED, MODIFIED, DOC_CHANGED
}

@Serializable
data class MessageDiff(
    val messageName: String,
    val differences: List<DiffItem>,
    val status: MessageStatus
)

enum class MessageStatus {
    ADDED, REMOVED, CHANGED, UNCHANGED
}

class SchemaDiffService(private val schemaService: SchemaService) {

    fun compareVersions(versionFrom: String, versionTo: String): List<MessageDiff> {
        val schemasFrom = schemaService.getSchemasForVersion(versionFrom)
        val schemasTo = schemaService.getSchemasForVersion(versionTo)

        val allMessages = (schemasFrom.keys + schemasTo.keys).sorted()
        val results = mutableListOf<MessageDiff>()

        for (message in allMessages) {
            val fileFrom = schemasFrom[message]
            val fileTo = schemasTo[message]

            if (fileFrom == null && fileTo != null) {
                results.add(MessageDiff(message, emptyList(), MessageStatus.ADDED))
            } else if (fileFrom != null && fileTo == null) {
                results.add(MessageDiff(message, emptyList(), MessageStatus.REMOVED))
            } else if (fileFrom != null && fileTo != null) {
                val diffs = compareSchemaFiles(fileFrom, fileTo)
                if (diffs.isNotEmpty()) {
                    results.add(MessageDiff(message, diffs, MessageStatus.CHANGED))
                }
            }
        }

        return results
    }

    private fun compareSchemaFiles(file1: File, file2: File): List<DiffItem> {
        val ctx1 = SchemaContext(file1)
        val ctx2 = SchemaContext(file2)

        val elements1 = ctx1.flatten()
        val elements2 = ctx2.flatten()

        val diffs = mutableListOf<DiffItem>()
        val allPaths = (elements1.keys + elements2.keys).sorted()

        for (path in allPaths) {
            val elem1 = elements1[path]
            val elem2 = elements2[path]

            if (elem1 == null && elem2 != null) {
                diffs.add(DiffItem(path, DiffType.ADDED, "Element added", null, elem2.documentation))
            } else if (elem1 != null && elem2 == null) {
                diffs.add(DiffItem(path, DiffType.REMOVED, "Element removed", elem1.documentation, null))
            } else if (elem1 != null && elem2 != null) {
                // Check documentation
                if (elem1.documentation != elem2.documentation) {
                    diffs.add(
                        DiffItem(
                            path,
                            DiffType.DOC_CHANGED,
                            "Documentation changed",
                            elem1.documentation,
                            elem2.documentation
                        )
                    )
                }

                // Check type name (if both have types)
                // Note: In flattened view, we mostly care about leaf types or structural changes.
                // If a type changed from String to Integer, that matters.
                // If a ComplexType name changed but structure is identical, it might effectively be unchanged logic-wise,
                // but for now let's flag explicit type name changes if they are simple types.
                if (elem1.typeName != elem2.typeName) {
                     diffs.add(DiffItem(path, DiffType.MODIFIED, "Type changed", elem1.typeName, elem2.typeName))
                }
            }
        }

        return diffs
    }

    private class SchemaContext(val rootFile: File) {
        // Namespace -> (TypeName -> Element)
        val typeRegistry = mutableMapOf<String, MutableMap<String, Element>>()
        val flattenedElements = mutableMapOf<String, SchemaElement>()
        val visitedTypes = mutableSetOf<String>() // For cycle detection during recursive flattening logic per path

        // Pre-loaded DOMs to keep them in memory
        val loadedDocuments = mutableMapOf<String, Element>()

        init {
            loadAndIndex(rootFile)
        }
        
        private fun loadAndIndex(file: File) {
            if (loadedDocuments.containsKey(file.absolutePath)) return
            
            try {
                val factory = DocumentBuilderFactory.newInstance()
                factory.isNamespaceAware = true
                val builder = factory.newDocumentBuilder()
                val doc = builder.parse(file).documentElement
                loadedDocuments[file.absolutePath] = doc
                
                val targetNamespace = doc.getAttribute("targetNamespace") ?: ""
                
                // Index types in this file
                val children = doc.childNodes
                for (i in 0 until children.length) {
                    val node = children.item(i)
                    if (node.nodeType == Node.ELEMENT_NODE) {
                         val el = node as Element
                         val name = el.getAttribute("name")
                         if (name.isNotEmpty()) {
                             if (el.localName == "complexType" || el.localName == "simpleType") {
                                 typeRegistry.getOrPut(targetNamespace) { mutableMapOf() }[name] = el
                             }
                         }
                         
                         // Handle imports/includes
                         if (el.localName == "import" || el.localName == "include") {
                             val location = el.getAttribute("schemaLocation")
                             if (location.isNotEmpty()) {
                                 val importedFile = File(file.parentFile, location)
                                 if (importedFile.exists()) {
                                     loadAndIndex(importedFile)
                                 }
                             }
                         }
                    }
                }
            } catch (e: Exception) {
                println("Failed to load schema: ${file.absolutePath} - ${e.message}")
            }
        }
        
        fun flatten(): Map<String, SchemaElement> {
            val rootDoc = loadedDocuments[rootFile.absolutePath] ?: return emptyMap()
            // Find the main element (assuming same name as file without extension, commonly IATA_...)
            // But usually there is only one root xs:element in the main file for these messages.
            
            val children = rootDoc.childNodes
            for (i in 0 until children.length) {
                val node = children.item(i)
                if (node.nodeType == Node.ELEMENT_NODE && (node as Element).localName == "element") {
                     expandElement(node, "")
                }
            }
            return flattenedElements
        }
        
        private fun expandElement(element: Element, currentPath: String, activeTypes: Set<String> = emptySet()) {
            val name = element.getAttribute("name")
            if (name.isEmpty()) return // Should interpret 'ref' here ideally, but assuming 'name' for now
            
            val newPath = if (currentPath.isEmpty()) name else "$currentPath/$name"
            
            val doc = getDocumentation(element)
            val typeVal = element.getAttribute("type")
            
            flattenedElements[newPath] = SchemaElement(name, typeVal, doc)
            
            if (typeVal.isNotEmpty()) {
                // Resolve type
                val parts = typeVal.split(":")
                val prefix = if (parts.size > 1) parts[0] else ""
                val localTypeName = if (parts.size > 1) parts[1] else parts[0]
                
                val namespace = resolveNamespace(element, prefix)
                
                // Cycle detection
                val typeKey = "$namespace:$localTypeName"
                if (activeTypes.contains(typeKey)) return
                
                val typeDef = typeRegistry[namespace]?.get(localTypeName)
                if (typeDef != null) {
                    expandType(typeDef, newPath, activeTypes + typeKey)
                }
            } else {
                 // Inline complexType?
                 val complexType = findChild(element, "complexType")
                 if (complexType != null) {
                     expandType(complexType, newPath, activeTypes)
                 }
            }
        }
        
        private fun expandType(typeElement: Element, currentPath: String, activeTypes: Set<String>) {
            // traverse sequence, choice, complexContent, extension
            val children = typeElement.childNodes
            for (i in 0 until children.length) {
                traverseStructure(children.item(i), currentPath, activeTypes)
            }
        }
        
        private fun traverseStructure(node: Node, currentPath: String, activeTypes: Set<String>) {
             if (node.nodeType != Node.ELEMENT_NODE) return
             val el = node as Element
             
             when (el.localName) {
                 "element" -> expandElement(el, currentPath, activeTypes)
                 "sequence", "choice", "all" -> {
                     val children = el.childNodes
                     for (i in 0 until children.length) {
                         traverseStructure(children.item(i), currentPath, activeTypes)
                     }
                 }
                 "complexContent" -> {
                     val extension = findChild(el, "extension")
                     if (extension != null) {
                         // Base type expansion
                         val base = extension.getAttribute("base")
                         if (base.isNotEmpty()) {
                             val parts = base.split(":")
                             val prefix = if (parts.size > 1) parts[0] else ""
                             val localTypeName = if (parts.size > 1) parts[1] else parts[0]
                             val namespace = resolveNamespace(el, prefix)
                             
                             val typeKey = "$namespace:$localTypeName"
                             if (!activeTypes.contains(typeKey)) {
                                 val baseTypeDef = typeRegistry[namespace]?.get(localTypeName)
                                 if (baseTypeDef != null) {
                                     expandType(baseTypeDef, currentPath, activeTypes + typeKey)
                                 }
                             }
                         }
                         // Then extension own elements
                         val children = extension.childNodes
                         for (i in 0 until children.length) {
                             traverseStructure(children.item(i), currentPath, activeTypes)
                         }
                     }
                 }
             }
        }

        private fun resolveNamespace(element: Element, prefix: String): String {
            if (prefix.isEmpty()) {
                // Default namespace lookup
                // In DOM, often xmlns is an attribute
                 // This is simplified. Proper implementation needs to climb up parent nodes.
                 // For now, assuming standard one defined at root or checking current element.
                 return element.ownerDocument.documentElement.getAttribute("targetNamespace") ?: ""
            }
            
            // Look for xmlns:prefix
            var current: Node? = element
            val nsAttr = "xmlns:$prefix"
            while (current != null && current.nodeType == Node.ELEMENT_NODE) {
                val el = current as Element
                if (el.hasAttribute(nsAttr)) {
                    return el.getAttribute(nsAttr)
                }
                current = current.parentNode
            }
            return ""
        }
        
        private fun findChild(parent: Element, localName: String): Element? {
            val children = parent.childNodes
            for (i in 0 until children.length) {
                val node = children.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    val e = node as Element
                    if (e.localName == localName) return e
                }
            }
            return null
        }
        
        private fun getDocumentation(element: Element): String? {
             val annotation = findChild(element, "annotation") ?: return null
             val documentation = findChild(annotation, "documentation") ?: return null
             return documentation.textContent.trim()
        }

    }
    
    data class SchemaElement(
        val name: String,
        val typeName: String,
        val documentation: String?
    )
}
