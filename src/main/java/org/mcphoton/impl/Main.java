package org.mcphoton.impl;

import org.mcphoton.Photon;

/**
 * Main class, which launches the program.
 *
 * @author TheElectronWill
 *
 */
public class Main {

	static final String os = System.getProperty("os.name");

	public static void main(String[] args) {
		printFramed("Photon server version " + Photon.getVersion(), "For minecraft version " + Photon.getMinecraftVersion());
		ServerCreator serverCreator = new ServerCreator("PhotonServer");
		PhotonServer server = serverCreator.createServer();
		server.startThreads();
	}

	private static void printFramed(String... strings) {
		int max = 0;
		for (String s : strings) {
			if (s.length() > max) {
				max = s.length();
			}
		}

		for (int i = 0; i < max + 4; i++) {
			System.out.print('-');
		}
		System.out.println();

		for (String s : strings) {
			System.out.print("| ");
			System.out.print(s);
			for (int i = 0; i < max - s.length(); i++) {
				System.out.print(' ');
			}
			System.out.println(" |");
		}

		for (int i = 0; i < max + 4; i++) {
			System.out.print('-');
		}
		System.out.println();
	}

}