/*
 * Copyright (c) 2016 MCPhoton <http://mcphoton.org> and contributors.
 *
 * This file is part of the Photon Server Implementation <https://github.com/mcphoton/Photon-Server>.
 *
 * The Photon Server Implementation is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Photon Server Implementation is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.mcphoton.impl.plugin;

import com.electronwill.utils.SimpleBag;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.mcphoton.Photon;
import org.mcphoton.impl.plugin.DependencyResolver.Solution;
import org.mcphoton.plugin.ClassSharer;
import org.mcphoton.plugin.Plugin;
import org.mcphoton.plugin.PluginDescription;
import org.mcphoton.plugin.ServerPlugin;
import org.mcphoton.plugin.ServerPluginsManager;
import org.mcphoton.plugin.SharedClassLoader;
import org.mcphoton.plugin.WorldPlugin;
import org.mcphoton.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of ServerPluginsManager
 *
 * @author TheElectronWill
 */
public final class ServerPluginsManagerImpl implements ServerPluginsManager {

	static final Logger LOGGER = LoggerFactory.getLogger("PluginsManager");
	static final ClassSharer GLOBAL_CLASS_SHARER = new ClassSharerImpl();
	private final Map<String, ServerPlugin> serverPlugins = new HashMap<>();

	@Override
	public ClassSharer getClassSharer() {
		return GLOBAL_CLASS_SHARER;
	}

	@Override
	public ServerPlugin getServerPlugin(String name) {
		return serverPlugins.get(name);
	}

	@Override
	public boolean isServerPluginLoaded(String name) {
		return serverPlugins.containsKey(name);
	}

	@Override
	public Plugin loadPlugin(File file) throws Exception {
		return loadPlugin(file, Photon.getServer().getWorlds());
	}

	@Override
	public Plugin loadPlugin(File file, Collection<World> worlds) throws Exception {
		final PluginClassLoader classLoader = new PluginClassLoader(file.toURI().toURL(), GLOBAL_CLASS_SHARER);
		final Class<? extends Plugin> clazz = PluginClassFinder.findPluginClass(file, classLoader);
		if (clazz == null) {
			throw new PluginClassNotFoundException(file);
		}
		final PluginDescription description = clazz.getAnnotation(PluginDescription.class);

		if (ServerPlugin.class.isAssignableFrom(clazz)) {//ServerPlugin -> one global instance.
			if (description == null) {
				throw new MissingPluginDescriptionException(clazz);
			}
			ServerPlugin instance = (ServerPlugin) clazz.newInstance();

			Collection<World> worldsCopy = new SimpleBag<>(worlds.size());
			worldsCopy.addAll(worlds);

			instance.init(description, worldsCopy);
			for (World world : worldsCopy) {
				world.getPluginsManager().registerPlugin(instance);
				classLoader.increaseUseCount();
			}
			return instance;

		} else if (WorldPlugin.class.isAssignableFrom(clazz)) {//WorldPlugin -> one instance per world.
			if (description == null) {
				throw new MissingPluginDescriptionException(clazz);
			}
			WorldPlugin instance = null;
			for (World world : worlds) {
				instance = (WorldPlugin) clazz.newInstance();
				instance.init(description, world);
				world.getPluginsManager().registerPlugin(instance);
				classLoader.increaseUseCount();
			}
			return instance;//return the plugin instance of the last world.

		} else {//Unknown type of plugin.
			//Don't need to check for a PluginDescription, because this type of plugin doesn't need to be initialized by the PluginsManager.
			Plugin instance = null;
			for (World world : worlds) {
				instance = clazz.newInstance();
				world.getPluginsManager().registerPlugin(instance);
				classLoader.increaseUseCount();
			}
			return instance;//return the plugin instance of the last world.
		}
	}

	/**
	 * Loads plugins from multiple files.
	 *
	 * @param files the files to load the plugins from (1 plugin per file).
	 * @param worldPlugins the plugins to load for each world.
	 * @param serverPlugins the plugins to load for the entire server. They don't have to extend ServerPlugin.
	 */
	public void loadPlugins(File[] files, Map<World, List<String>> worldPlugins, List<String> serverPlugins, List<World> serverWorlds) throws Exception {
		final Map<String, PluginInfos> infosMap = new HashMap<>();
		final List<String> serverPluginsVersions = new ArrayList<>(serverPlugins.size());
		final List<PluginInfos> nonGlobalServerPlugins = new ArrayList<>();//for step 4

		//1: Gather informations about the plugins: class + description.
		LOGGER.debug("Gathering informations about the plugins...");
		for (File file : files) {
			PluginClassLoader classLoader = new PluginClassLoader(file.toURI().toURL(), GLOBAL_CLASS_SHARER);
			Class<? extends Plugin> clazz = PluginClassFinder.findPluginClass(file, classLoader);
			if (clazz == null) {
				throw new PluginClassNotFoundException(file);
			}
			/*
			 * Here we DO need a PluginDescription for every plugin because to resolve the dependencies we
			 * need to have some informations about the plugin before creating its instance.
			 */
			PluginDescription description = clazz.getAnnotation(PluginDescription.class);
			if (description == null) {
				throw new MissingPluginDescriptionException(clazz);
			}
			PluginInfos infos = new PluginInfos(clazz, description);
			infosMap.put(description.name(), infos);
			LOGGER.trace("Valid plugin found: {} -> infos: {}.", file, infos);
		}

		//2: Resolve dependencies for the ServerPlugins and load them.
		//2.1: Resolve dependencies for the *actual* ServerPlugins.
		LOGGER.debug("Resolving dependencies for the actual server plugins...");
		DependencyResolver resolver = new DependencyResolver();
		for (Iterator<String> it = serverPlugins.iterator(); it.hasNext();) {
			String plugin = it.next();
			PluginInfos infos = infosMap.get(plugin);
			if (ServerPlugin.class.isAssignableFrom(infos.clazz)) {//actual ServerPlugin
				serverPluginsVersions.add(infos.description.version());
				resolver.addToResolve(infos.description);
			} else {//not a ServerPlugin -> distribute to every world
				it.remove();
				for (Map.Entry<World, List<String>> entry : worldPlugins.entrySet()) {
					entry.getValue().add(plugin);
				}
			}
		}
		Solution solution = resolver.resolve();
		LOGGER.debug("Solution: {}", solution.resolvedOrder);

		//2.2: Print informations.
		LOGGER.info("{} out of {} server plugins will be loaded.", solution.resolvedOrder.size(), serverPluginsVersions.size());
		for (Exception ex : solution.errors) {
			LOGGER.error(ex.toString());
		}

		//2.3: Load the server plugins.
		LOGGER.debug("Loading the server plugins...");
		for (String plugin : solution.resolvedOrder) {
			PluginInfos infos = infosMap.get(plugin);
			try {
				ServerPlugin instance = (ServerPlugin) infos.clazz.newInstance();
				instance.init(infos.description, serverWorlds);
				instance.onLoad();
			} catch (Exception ex) {
				LOGGER.error("Unable to load the plugin {}.", plugin, ex);
			}
		}

		//3: Resolve dependencies for the other (non server) plugins, per world, and load them.
		LOGGER.info("Loading plugins per world...");
		for (Map.Entry<World, List<String>> entry : worldPlugins.entrySet()) {
			final World world = entry.getKey();
			final List<String> plugins = entry.getValue();

			//3.1: Resolve dependencies for the world's plugins.
			LOGGER.debug("Resolving dependencies for the plugins of the world {}...", world.getName());
			resolver = new DependencyResolver();
			resolver.addAvailable(serverPlugins, serverPluginsVersions);//the server plugins are available to all the plugins
			for (String plugin : plugins) {
				PluginInfos infos = infosMap.get(plugin);
				resolver.addToResolve(infos.description);
			}
			solution = resolver.resolve();
			LOGGER.debug("Solution: {}", solution.resolvedOrder);

			//3.2: Print informations.
			LOGGER.info("{} out of {} plugins will be loaded in world {}.", solution.resolvedOrder.size(), plugins.size(), world);
			for (Exception ex : solution.errors) {
				LOGGER.error(ex.toString());
			}

			//3.3: Load the world's plugins.
			LOGGER.debug("Loading the plugins in world {}...", world.getName());
			for (String plugin : solution.resolvedOrder) {
				PluginInfos infos = infosMap.get(plugin);
				try {
					if (ServerPlugin.class.isAssignableFrom(infos.clazz)) {
						//ServerPlugins need a Collection<World> so we "collect" all the worlds first and load them later, at step 4.
						infos.getWorlds().add(world);
						nonGlobalServerPlugins.add(infos);
					} else if (WorldPlugin.class.isAssignableFrom(infos.clazz)) {
						WorldPlugin instance = (WorldPlugin) infos.clazz.newInstance();
						instance.init(infos.description, world);
						instance.onLoad();
						world.getPluginsManager().registerPlugin(instance);
					} else {
						Plugin instance = infos.clazz.newInstance();
						instance.onLoad();
						world.getPluginsManager().registerPlugin(instance);
					}
					SharedClassLoader loader = (SharedClassLoader) infos.clazz.getClassLoader();
					loader.increaseUseCount();
				} catch (Exception ex) {
					LOGGER.error("Unable to load the plugin {}.", plugin, ex);
				}
			}
		}

		//4: Actually load the server plugins that aren't loaded on the entire server.
		LOGGER.info("Loading the non global server plugins...");
		for (PluginInfos infos : nonGlobalServerPlugins) {
			try {
				ServerPlugin instance = (ServerPlugin) infos.clazz.newInstance();
				instance.init(infos.description, infos.worlds);
				instance.onLoad();
				for (World world : infos.worlds) {
					world.getPluginsManager().registerPlugin(instance);
				}
			} catch (Exception ex) {
				LOGGER.error("Unable to load the plugin {}.", infos.description.name(), ex);
			}
		}
	}

	@Override
	public void unloadServerPlugin(ServerPlugin plugin) {
		; //TODO
	}

	@Override
	public void unloadServerPlugin(String name) {
		; //TODO
	}

	private class PluginInfos {

		final Class<? extends Plugin> clazz;
		final PluginDescription description;
		Collection<World> worlds;

		PluginInfos(Class<? extends Plugin> clazz, PluginDescription description) {
			this.clazz = clazz;
			this.description = description;
		}

		Collection<World> getWorlds() {
			if (worlds == null) {
				worlds = Collections.synchronizedCollection(new SimpleBag<>());//synchronized because any thread could use it
			}
			return worlds;
		}

	}

}
