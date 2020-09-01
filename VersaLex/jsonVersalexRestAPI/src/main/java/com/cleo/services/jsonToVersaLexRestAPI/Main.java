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

        return options;
    }

    public static void checkHelp(CommandLine cmd) {
        if (cmd.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("JsonToVersalexRESTAPI", getOptions());
            System.exit(0);
        }
    }

    public static Profile loadProfile(String name) {
        TypeReference<Map<String, Profile>> typeRef = new TypeReference<Map<String, Profile>>() {
        };
        Path cic = Paths.get(System.getProperty("user.home"), ".cic");
        try {
            Map<String, Profile> profiles = mapper.readValue(cic.resolve("profiles").toFile(), typeRef);
            return profiles.get(name);
        } catch (JsonParseException | JsonMappingException e) {
            System.err.println("error parsing file $HOME/.cic/profiles: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("error loading file $HOME/.cic/profiles: " + e.getMessage());
        }
        return null;
    }

    public static void saveProfile(String name, Profile profile) {
        TypeReference<Map<String, Profile>> typeRef = new TypeReference<Map<String, Profile>>() {
        };
        Path cic = Paths.get(System.getProperty("user.home"), ".cic");
        if (!cic.toFile().isDirectory()) {
            cic.toFile().mkdir();
        }
        try {
            File file = cic.resolve("profiles").toFile();
            Map<String, Profile> profiles;
            try {
                profiles = mapper.readValue(file, typeRef);
            } catch (IOException e) {
                profiles = new HashMap<>();
            }
            profiles.put(name, profile);
            mapper.writeValue(cic.resolve("profiles").toFile(), profiles);
        } catch (JsonParseException | JsonMappingException e) {
            System.err.println("error parsing file $HOME/.cic/profiles: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("error updating file $HOME/.cic/profiles: " + e.getMessage());
        }
    }

    public static Profile processProfileOptions(CommandLine cmd) throws Exception {
        Profile profile = null;
        List<String> missing = new ArrayList<>();
        if (cmd.hasOption("profile")) {
            profile = loadProfile(cmd.getOptionValue("profile"));
        }
        if (profile == null) {
            profile = new Profile();
        }
        if (cmd.hasOption("hostname")) {
            profile.setHost(cmd.getOptionValue("hostname"));
        }
        if (cmd.hasOption("port")) {
            profile.setPort(Integer.parseInt(cmd.getOptionValue("port")));
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
        if (cmd.hasOption("save")) {
            saveProfile(cmd.getOptionValue("profile", "default"), profile);
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
        if (!missing.isEmpty()) {
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

        REST restClient = null;
        try {
            restClient = new REST("http://" + profile.getHost(), profile.getPort(), profile.getUsername(),
                    profile.getPassword());
        } catch (Exception ex) {
            System.out.println("Failed to create REST Client: " + ex.getMessage());
            System.exit(-1);
        }
        if (cmd.hasOption("input")) {
            VersalexRestBatchProcessor processor = new VersalexRestBatchProcessor(restClient)
                    .set(generatePass, cmd.hasOption("generate-pass")).set(update, cmd.hasOption("update"));
            if (!Strings.isNullOrEmpty(profile.getExportPassword())) {
                processor.setExportPassword(profile.getExportPassword());
            }
            processor.processFiles(cmd.getOptionValues("input"));
        }
    }

}
