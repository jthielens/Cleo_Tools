package com.cleo.services.jsonToVersaLexRestAPI;

import static com.cleo.services.jsonToVersaLexRestAPI.VersalexRestBatchProcessor.Option.generatePass;
import static com.cleo.services.jsonToVersaLexRestAPI.VersalexRestBatchProcessor.Option.update;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Strings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Main {

    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    private static Options getOptions() {
        Options options = new Options();

        options.addOption(Option.builder()
                .longOpt("help")
                .build());

        options.addOption(Option.builder("h")
                .longOpt("hostname")
                .desc("VersaLex hostname")
                .hasArg()
                .argName("HOSTNAME")
                .required(false)
                .build());

        options.addOption(Option.builder()
                .longOpt("port")
                .desc("VersaLex HTTP Port")
                .hasArg()
                .argName("PORT")
                .required(false)
                .build());

        options.addOption(Option.builder("s")
                .longOpt("secure")
                .desc("Use https instead of http")
                .required(false)
                .build());

        options.addOption(Option.builder("k")
                .longOpt("insecure")
                .desc("Disable https security checks")
                .required(false)
                .build());

        options.addOption(Option.builder("u")
                .longOpt("username")
                .desc("Username")
                .hasArg()
                .argName("USERNAME")
                .required(false)
                .build());

        options.addOption(Option.builder("p")
                .longOpt("password")
                .desc("Password")
                .hasArg()
                .argName("PASSWORD")
                .required(false)
                .build());

        options.addOption(Option.builder("i")
                .longOpt("input")
                .desc("input file YAML, JSON or CSV")
                .hasArg()
                .argName("FILE")
                .required(false)
                .build());

        options.addOption(Option.builder()
                .longOpt("generate-pass")
                .desc("Generate Passwords for users")
                .required(false)
                .build());

        options.addOption(Option.builder()
                .longOpt("export-pass")
                .desc("Password to encrypt generated passwords")
                .hasArg()
                .argName("PASSWORD")
                .required(false)
                .build());

        options.addOption(Option.builder()
                .longOpt("update")
                .desc("Updates existing hosts")
                .required(false)
                .build());

        options.addOption(Option.builder()
                .longOpt("profile")
                .desc("Connection profile to use")
                .hasArg()
                .argName("PROFILE")
                .required(false)
                .build());

        options.addOption(Option.builder()
                .longOpt("save")
                .desc("Save/update profile")
                .required(false)
                .build());

        options.addOption(Option.builder()
                .longOpt("remove")
                .desc("Remove profile")
                .required(false)
                .build());

        return options;
    }

    public static void checkHelp(CommandLine cmd) {
        if (cmd.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("JsonToVersalexRESTAPI", getOptions());
            System.exit(0);
        }
    }

    public static Profile loadProfile(String name, boolean quiet) {
        TypeReference<Map<String, Profile>> typeRef = new TypeReference<Map<String, Profile>>() {};
        Path cic = Paths.get(System.getProperty("user.home"), ".cic");
        Path filename = cic.resolve("profiles");
        try {
            Map<String, Profile> profiles = mapper.readValue(filename.toFile(), typeRef);
            return profiles.get(name);
        } catch (JsonParseException | JsonMappingException e) {
            System.err.println("error parsing file "+filename+": "+ e.getMessage());
        } catch (IOException e) {
            if (!quiet) {
                System.err.println("error loading file "+filename+": " + e.getMessage());
            }
        }
        return null;
    }

    public static void removeProfile(String name, Profile profile) {
        TypeReference<Map<String, Profile>> typeRef = new TypeReference<Map<String, Profile>>() {};
        Path cic = Paths.get(System.getProperty("user.home"), ".cic");
        Path filename = cic.resolve("profiles");
        try {
            File file = filename.toFile();
            Map<String, Profile> profiles;
            try {
                profiles = mapper.readValue(file, typeRef);
            } catch (IOException e) {
                System.err.println(filename+" not found while removing profile "+name+": "+e.getMessage());
                return; // no file, nothing to remove
            }
            if (!profiles.containsKey(name)) {
                System.err.println("profile "+name+" not found in "+filename);
                return; // nothing to remove
            }
            profiles.remove(name);
            mapper.writeValue(filename.toFile(), profiles);
        } catch (JsonParseException | JsonMappingException e) {
            System.err.println("error parsing file "+filename+": " + e.getMessage());
        } catch (IOException e) {
            System.err.println("error updating file "+filename+": " + e.getMessage());
        }
    }

    public static void saveProfile(String name, Profile profile) {
        TypeReference<Map<String, Profile>> typeRef = new TypeReference<Map<String, Profile>>() {};
        Path cic = Paths.get(System.getProperty("user.home"), ".cic");
        Path filename = cic.resolve("profiles");
        if (!cic.toFile().isDirectory()) {
            cic.toFile().mkdir();
        }
        try {
            File file = filename.toFile();
            Map<String, Profile> profiles;
            try {
                profiles = mapper.readValue(file, typeRef);
            } catch (IOException e) {
                profiles = new HashMap<>();
            }
            profiles.put(name, profile);
            mapper.writeValue(filename.toFile(), profiles);
        } catch (JsonParseException | JsonMappingException e) {
            System.err.println("error parsing file "+filename+": " + e.getMessage());
        } catch (IOException e) {
            System.err.println("error updating file "+filename+": " + e.getMessage());
        }
    }

    public static Profile processProfileOptions(CommandLine cmd) throws Exception {
        Profile profile = null;
        List<String> missing = new ArrayList<>();
        profile = loadProfile(cmd.getOptionValue("profile", "default"),
                !cmd.hasOption("profile") || cmd.hasOption("remove") || cmd.hasOption("save"));
        if (profile == null) {
            profile = new Profile();
        }
        if (cmd.hasOption("hostname")) {
            profile.setHost(cmd.getOptionValue("hostname"));
        }
        if (cmd.hasOption("port")) {
            profile.setPort(Integer.parseInt(cmd.getOptionValue("port")));
        }
        if (cmd.hasOption("secure")) {
            profile.setSecure(true);
        }
        if (cmd.hasOption("insecure")) {
            profile.setInsecure(true);
        }
        if (cmd.hasOption("username")) {
            profile.setUsername(cmd.getOptionValue("username"));
        }
        if (cmd.hasOption("password")) {
            profile.setPassword(cmd.getOptionValue("password"));
        }
        if (cmd.hasOption("export-pass")) {
            profile.setExportPassword(cmd.getOptionValue("export-pass"));
        }
        if (Strings.isNullOrEmpty(profile.getHost())) {
            missing.add("hostname (h)");
        }
        if (profile.getPort() < 0) {
            missing.add("port");
        }
        if (Strings.isNullOrEmpty(profile.getUsername())) {
            missing.add("username (u)");
        }
        if (Strings.isNullOrEmpty(profile.getPassword())) {
            missing.add("password (p)");
        }
        if (cmd.hasOption("remove")) {
            removeProfile(cmd.getOptionValue("profile", "default"), profile);
        }
        if (cmd.hasOption("save")) {
            if (!missing.isEmpty()) {
                throw new Exception("Missing required options for --save: "
                    + missing.stream().collect(Collectors.joining(", ")));
            }
            saveProfile(cmd.getOptionValue("profile", "default"), profile);
        }
        if (!missing.isEmpty() && cmd.hasOption("input")) {
            throw new Exception("Missing required options or profile values: "
                    + missing.stream().collect(Collectors.joining(", ")));
        }
        return profile;
    }

    public static void main(String[] args) throws IOException {
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
            Options options = getOptions();
            cmd = parser.parse(options, args);
            checkHelp(cmd);
        } catch (Exception ex) {
            System.out.println("Could not parse command line arguments: " + ex.getMessage());
            System.exit(-1);
        }

        Profile profile = null;
        try {
            profile = processProfileOptions(cmd);
        } catch (Exception e) {
            System.out.println("Could not parse command line arguments: " + e.getMessage());
            System.exit(-1);
        }

        if (cmd.hasOption("input")) {
            REST restClient = null;
            try {
                String protocol = profile.isSecure() ? "https" : "http";
                restClient = new REST(protocol + "://" + profile.getHost(), profile.getPort(), profile.getUsername(),
                        profile.getPassword(), profile.isInsecure());
            } catch (Exception ex) {
                System.out.println("Failed to create REST Client: " + ex.getMessage());
                System.exit(-1);
            }
            VersalexRestBatchProcessor processor = new VersalexRestBatchProcessor(restClient)
                    .set(generatePass, cmd.hasOption("generate-pass")).set(update, cmd.hasOption("update"));
            if (!Strings.isNullOrEmpty(profile.getExportPassword())) {
                processor.setExportPassword(profile.getExportPassword());
            }
            processor.processFiles(cmd.getOptionValues("input"));
        }
    }

}
