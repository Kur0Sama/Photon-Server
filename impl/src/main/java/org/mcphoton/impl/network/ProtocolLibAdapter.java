package org.mcphoton.impl.network;

import com.github.steveice10.mc.protocol.MinecraftConstants;
import com.github.steveice10.mc.protocol.MinecraftProtocol;
import com.github.steveice10.packetlib.Server;
import com.github.steveice10.packetlib.event.server.ServerAdapter;
import com.github.steveice10.packetlib.event.server.SessionAddedEvent;
import com.github.steveice10.packetlib.event.server.SessionRemovedEvent;
import com.github.steveice10.packetlib.event.session.ConnectedEvent;
import com.github.steveice10.packetlib.event.session.DisconnectedEvent;
import com.github.steveice10.packetlib.event.session.PacketReceivedEvent;
import com.github.steveice10.packetlib.event.session.PacketSentEvent;
import com.github.steveice10.packetlib.event.session.SessionAdapter;
import com.github.steveice10.packetlib.packet.Packet;
import com.github.steveice10.packetlib.tcp.TcpSessionFactory;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.mcphoton.Photon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Makes the ProtocolLib easily usable by the photon server.
 *
 * @author TheElectronWill
 */
public final class ProtocolLibAdapter {
	private static final Logger logger = LoggerFactory.getLogger(ProtocolLibAdapter.class);

	private final Server libServer;
	private final Map<Class<? extends Packet>, List<Consumer<? extends Packet>>> handlersMap = new HashMap<>();

	public ProtocolLibAdapter(int port) {
		this.libServer = new Server("localhost", port, MinecraftProtocol.class,
									new TcpSessionFactory(Proxy.NO_PROXY));
	}

	/**
	 * Starts the protocol lib threads and start accepting client connections.
	 */
	public void start() {
		logger.info("Starting the network part...");
		logger.debug("Configuring the ProtocolLib...");
		libServer.setGlobalFlag(MinecraftConstants.AUTH_PROXY_KEY, Proxy.NO_PROXY);
		libServer.setGlobalFlag(MinecraftConstants.VERIFY_USERS_KEY,
								Photon.getServer().getConfiguration().isOnlineMode());
		libServer.setGlobalFlag(MinecraftConstants.SERVER_INFO_BUILDER_KEY, new InfoBuilderImpl());
		libServer.setGlobalFlag(MinecraftConstants.SERVER_INFO_BUILDER_KEY, new LoginHandlerImpl());
		libServer.setGlobalFlag(MinecraftConstants.SERVER_COMPRESSION_THRESHOLD, -1);
		libServer.addListener(new ServerAdapter() {
			@Override
			public void sessionAdded(SessionAddedEvent event) {
				logger.debug("Session added: {}", event.getSession().getHost());
				event.getSession().addListener(new SessionAdapter() {
					@Override
					public void packetReceived(PacketReceivedEvent event) {
						Packet packet = event.getPacket();
						logger.trace("Packet received {} from {}", packet, event.getSession().getHost());
						List<Consumer<? extends Packet>> consumerList = handlersMap.get(packet.getClass());
						if (consumerList != null) {
							logger.trace("Consume event {}", event);
							for (Consumer consumer : consumerList) {
								logger.trace("*Consumer " + consumer);
								consumer.accept(packet);
							}
						}
					}

					@Override
					public void packetSent(PacketSentEvent event) {
						logger.warn("Unhandled: packet sent {} to {}", event.getPacket(),
									event.getSession().getHost());
					}

					@Override
					public void connected(ConnectedEvent event) {
						logger.warn("Unhandled: client session connected: {}",
									event.getSession().getHost());
					}

					@Override
					public void disconnected(DisconnectedEvent event) {
						logger.warn("Unhandled: client session disconnected: {}",
									event.getSession().getHost());
					}
				});
			}

			@Override
			public void sessionRemoved(SessionRemovedEvent event) {
				logger.warn("Unhandled: session removed: {}", event.getSession().getHost());
			}
		});
		logger.debug("Initializing the protocol server...");
		libServer.bind();
		logger.debug("The protocol server is ready.");
	}

	/**
	 * Stops the protocol lib threads and close the client connections.
	 */
	public void stop() {
		libServer.close();
	}

	public void addHandler(Class<? extends Packet> packetClass,
						   Consumer<? extends Packet> handler) {
		List<Consumer<? extends Packet>> consumerList = handlersMap.get(packetClass);
		if (consumerList == null) {
			consumerList = new ArrayList<>();
		}
		consumerList.add(handler);
	}

	public void removeHandler(Class<? extends Packet> packetClass,
							  Consumer<? extends Packet> handler) {
		List<Consumer<? extends Packet>> consumerList = handlersMap.get(packetClass);
		if (consumerList != null) {
			consumerList.remove(handler);
		}
	}
}