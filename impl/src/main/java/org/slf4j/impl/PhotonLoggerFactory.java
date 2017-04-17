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
package org.slf4j.impl;

import com.electronwill.utils.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

public class PhotonLoggerFactory implements ILoggerFactory {

	private final ConcurrentMap<String, Logger> loggerMap = new ConcurrentHashMap<>();
	
	@Override
	public Logger getLogger(String name) {
		if(name.startsWith("org.mcphoton")){
			List<String> parts = new ArrayList<>();
			StringUtils.split(name, '.', parts);
			name = parts.get(parts.size()-1);//uses the last part
		}
		final String loggerName = name;//final String for the lambda expression
		return loggerMap.computeIfAbsent(name, (k) -> new PhotonLogger(loggerName));
	}

}