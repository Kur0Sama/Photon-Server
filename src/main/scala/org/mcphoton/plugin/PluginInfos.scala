package org.mcphoton.plugin

import java.util.jar.JarFile

import better.files.{File, ManagedResource}

import scala.util.{Failure, Success, Try}

/**
 * @author TheElectronWill
 */
final class PluginInfos(val name: String, val version: String, val requiredDeps: Seq[String],
						val optionalDeps: Seq[String], val pluginClassName: String,
						val urlClassLoader: OpenURLClassLoader) {
	def this(c: PluginInfosCompanion, className: String, cl: OpenURLClassLoader) = {
		this(c.Name, c.Version, c.RequiredDependencies, c.OptionalDependencies, className, cl)
	}
}

object PluginInfos {
	def inspect(file: File): Try[PluginInfos] = {
		val url = file.url
		val classLoader = new OpenURLClassLoader(url, classOf[PluginInfos].getClassLoader)
		val pluginClass: Try[Class[_ <: Plugin]] = loadPluginClass(file, classLoader)
		pluginClass.map(extractInfos(_, classLoader))
	}

	private def extractInfos(pluginClass: Class[_ <: Plugin], classLoader: OpenURLClassLoader): PluginInfos = {
		// Gets the plugin's informations from its companion object:
		try {
			val pluginClassName = pluginClass.getCanonicalName
			val companionClass = classLoader.findClass(pluginClassName + "$")
			val companionField = companionClass.getField("$MODULE")
			val companion = companionField.get(null).asInstanceOf[PluginInfosCompanion]
			new PluginInfos(companion, pluginClassName, classLoader)
		} catch {
			case e: Exception =>
				throw new PluginLoadingException("Unable to load the PluginInfosCompanion", e)
		}
	}

	private def loadPluginClass(file: File, classLoader: OpenURLClassLoader): Try[Class[_ <: Plugin]] = {
		def isNormalClassName(name: String) = name.indexOf('$') < 0 && name.endsWith(".class")

		def className(entryName: String) = {
			// Removes the trailing ".class" and replaces all '/' by '.'
			entryName.substring(0, entryName.length - 6).replace('/', '.')
		}

		// Gets the first class that inherits from Plugin
		for (jar: ManagedResource[JarFile] <- new JarFile(file.toJava).autoClosed) {
			val entries = jar.head.entries
			while (entries.hasMoreElements) {
				val entry = entries.nextElement()
				val entryName = entry.getName
				if (isNormalClassName(entryName)) {
					val clazz = classLoader.findClass(className(entryName))
					if (classOf[Plugin].isAssignableFrom(clazz)) {
						return Success(clazz.asInstanceOf[Class[_ <: Plugin]])
					}
				}
			}
		}
		Failure(new PluginLoadingException("No plugin class found"))
	}
}