package dev.anjo7778.sao.simulation;

import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;

public final class JarChecks {
    public static void main(String[] args) throws Exception {
        try (ZipFile jar = new ZipFile(args[0])) {
            var metadata = jar.getEntry("META-INF/neoforge.mods.toml");
            if (metadata == null) throw new AssertionError("Missing mod metadata");
            String toml;
            try (var stream = jar.getInputStream(metadata)) {
                toml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            for (String token : new String[] { "modId=\"sao\"", "version=\"" + args[1] + "\"", "versionRange=\"[" + args[2] + ",)\"", "versionRange=\"[1.21.1]\"" }) {
                if (!toml.contains(token)) throw new AssertionError("Missing metadata: " + token);
            }
            if (toml.contains("${")) throw new AssertionError("Unexpanded metadata");
            for (String name : new String[] {"SaoMod", "simulation/FloorCatalog", "simulation/FloorProgress", "simulation/AdventurerMind"}) {
                if (jar.getEntry("dev/anjo7778/sao/" + name + ".class") == null) throw new AssertionError("Missing class: " + name);
            }
        }
        System.out.println("PASS: mod JAR metadata and required classes");
    }
}
