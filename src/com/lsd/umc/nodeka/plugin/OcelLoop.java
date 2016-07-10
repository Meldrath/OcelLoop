package com.lsd.umc.nodeka.plugin;

import com.lsd.umc.script.ScriptInterface;
import com.lsd.umc.util.AnsiTable;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.lsd.umc.Client;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public class OcelLoop {

    private ScriptInterface script;
    private Client client;

    /*
     [L:0] R:Draalik X:2000000000 G:205023444 A:-372
     [Lag: 0] [Reply: guide] [Align: 797]
     L:0 R: X:442159262
     [L:0] [Ocellaris H:43028/43028 M:8884/8884 S:5001/5001 E:21049/21049] [A:-1000] []
     */
    private static final Pattern nonCombatPrompt = Pattern.compile("^(?:\\[?(?:Lag|L):\\s?\\d+\\]?)\\s\\[?(?:R|Reply|.+ H):?\\s?");
    /*
     [L:0] Ocellaris: (perfect condition) vs. roadrunner: (badly wounded)
     [Lag: 2000] [Coil: (perfect condition)] [novice healer: (covered in blood)]

     [L:0] [Darth H:61211/63111 M:16074/16074 S:15390/15888 E:52997/54001] [A:1000] []
     [Ocellaris](perfect condition) [Bayeshi guard](near death)
     */
    private static final Pattern combatPrompt = Pattern.compile("^(?:\\[?(?:Lag|L):\\s?\\\\d+\\]?)?\\s?(?:.+):\\s?\\((?:.+)\\)|\\[.+]\\(.+\\)\\s");
    private static final Pattern timeSplit = Pattern.compile("(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)");

    private static final Pattern xpCapPrompt = Pattern.compile("\\[(?:Exp|EXP|X|XP|Xp)?:\\s?(?:.+)(?:(?:1;37)|34|32|(?:1;34))m(\\d+)(?:.+)\\[37m(?:.+)\\]");
    private static final Pattern goldCap = Pattern.compile("\\[?G(?:old)?:\\s?(?:.+)1;33m(\\d+)(?:.+)\\[37m(?:.+)\\]?");

    private static final Pattern receiveSalvage = Pattern.compile("^You get (?:\\((\\d+)\\) )?\\< salvage \\> (?:.+)\\.$");
    private static final Pattern giveSalvage = Pattern.compile("^You give (?:\\((\\d+)\\) )?\\< salvage \\> (?:.+) to (?:a(?:n)?)?");
    private static final Pattern witherSalvage = Pattern.compile("^(?:\\((\\d+)\\) )?\\< salvage \\> (?:.+) you carry withers into dust\\.$");
    private static final Pattern givenSalvage = Pattern.compile("^(?:.+) gives you (?:\\((\\d+)\\) )?\\< salvage \\> (?:.+)\\.$");
    private static final Pattern droppedSalvage = Pattern.compile("^You drop (?:\\((\\d+)\\) )?\\< salvage \\> (?:.+)\\.$");
    private static final Pattern movedRooms = Pattern.compile("(?:.+) (?:\\( safe \\) )?\\[ exits: .+\\ ]");
    private static final Pattern pkFlag = Pattern.compile("\\[1;31mPKer");

    /*
     0 = off
     1 = xp
     2 = gold
     3 = salvage
     */
    private int loopingMode = 1;
    private int loopStatus = 2;
    private int currentXP = 0;
    private int currentSalvage = 0;
    private int startSalvageLoop = 5;
    private int startGoldLoop = 980000000;
    private int currentGold = 0;
    private int currentGoldEstimate = 0;
    private int ppm = 0;
    private String gnomeRoom;
    private boolean gettingSalvageAtGnome = false;
    private boolean inSpeedwalk;
    private boolean trainer;
    private String speedwalkToTrainer;
    private String speedwalkToSalvageGnome;
    private String speedwalkToZone;
    private String zoneToBeRan;
    private String trainingToBeDone;
    private boolean atTrainer = false;
    private String speedwalkToZoneFromTemple;
    private int xpModeSelector;
    private int goldModeSelector;

    public void init(ScriptInterface script) {
        this.script = script;

        script.parse("#rates reset");
        script.setVariable("OCELLOOP_CURRENTGOLD", "0");
        script.setVariable("OCELLOOP_CURRENTXP", "0");
        script.setVariable("OCELLOOP_ESTIMATEDGOLD", "0");
        script.setVariable("OCELLOOP_SALVAGE", "0");

        script.print(AnsiTable.getCode("yellow") + "OcelLoop Plugin loaded.\001");
        script.print(AnsiTable.getCode("yellow") + "Developed by Ocellaris");
        script.registerCommand("ocelloop", "com.lsd.umc.nodeka.plugin.OcelLoop", "menu");
    }

    public String menu(String args) {
        List<String> argArray = new ArrayList<>();
        Matcher m = Pattern.compile("([^\"]\\S*|\".+?\")\\s*").matcher(args);
        while (m.find()) {
            argArray.add(m.group(1).replace("\"", ""));
        }

        if (argArray.isEmpty() || argArray.size() > 2 || "".equals(argArray.get(0))) {
            this.script.capture(AnsiTable.getCode("light red") + "--------------------------------------------------------------------------\001");
            this.script.capture(AnsiTable.getCode("yellow") + "Syntax:\001");
            this.script.capture(AnsiTable.getCode("white") + " > #OcelLoop " + AnsiTable.getCode("yellow") + "help" + AnsiTable.getCode("white") + "\001");
            this.script.capture(AnsiTable.getCode("white") + " > #OcelLoop " + AnsiTable.getCode("yellow") + "pause" + AnsiTable.getCode("white") + "\001");
            this.script.capture(AnsiTable.getCode("white") + " > #OcelLoop " + AnsiTable.getCode("yellow") + "resume" + AnsiTable.getCode("white") + "\001");
            this.script.capture(AnsiTable.getCode("white") + " > #OcelLoop " + AnsiTable.getCode("yellow") + "status" + AnsiTable.getCode("white") + "\001");
            this.script.capture(AnsiTable.getCode("white") + " > #OcelLoop " + AnsiTable.getCode("yellow") + "xp" + AnsiTable.getCode("white") + "\001");
            this.script.capture(AnsiTable.getCode("white") + " > #OcelLoop " + AnsiTable.getCode("yellow") + "gold" + AnsiTable.getCode("white") + " <amount>\001");
            this.script.capture(AnsiTable.getCode("white") + " > #OcelLoop " + AnsiTable.getCode("yellow") + "salvage" + AnsiTable.getCode("white") + " <amount>\001");
            this.script.capture(AnsiTable.getCode("white") + " > #OcelLoop " + AnsiTable.getCode("yellow") + "setsalvage" + AnsiTable.getCode("white") + " <amount>\001");
            this.script.capture(AnsiTable.getCode("light red") + "--------------------------------------------------------------------------\001");
        }

        if ("help".equals(argArray.get(0))) {
            this.script.capture(AnsiTable.getCode("light red") + "--------------------------------------------------------------------------\001");
            this.script.capture(AnsiTable.getCode("yellow") + "Help/Tips:\001");
            this.script.capture(AnsiTable.getCode("white") + "   > \001");
            this.script.capture(AnsiTable.getCode("white") + "   > \001");
            this.script.capture(AnsiTable.getCode("white") + "TIP: \001");
            this.script.capture(AnsiTable.getCode("white") + "TIP: \001");
            this.script.capture(AnsiTable.getCode("white") + "TIP: \001");
            this.script.capture(AnsiTable.getCode("white") + "TIP: \001");
            this.script.capture(AnsiTable.getCode("white") + "TIP: \001");
            this.script.capture(AnsiTable.getCode("light red") + "--------------------------------------------------------------------------\001");
        }

        if ("status".equals(argArray.get(0))) {
            this.script.capture(AnsiTable.getCode("light red") + "--------------------------------------------------------------------------\001");
            this.script.capture(AnsiTable.getCode("white") + " > Plugin Status: " + AnsiTable.getCode("yellow") + String.valueOf(loopingMode));
            this.script.capture(AnsiTable.getCode("white") + " > Loop Status: " + AnsiTable.getCode("yellow") + String.valueOf(loopStatus));
            this.script.capture(AnsiTable.getCode("white") + " > XP: " + AnsiTable.getCode("yellow") + String.valueOf(currentXP));
            this.script.capture(AnsiTable.getCode("white") + " > Gold: " + AnsiTable.getCode("yellow") + String.valueOf(currentGold));
            this.script.capture(AnsiTable.getCode("white") + " > Gold Estimate: " + AnsiTable.getCode("yellow") + String.valueOf(currentGoldEstimate));
            this.script.capture(AnsiTable.getCode("white") + " > Salvage: " + AnsiTable.getCode("yellow") + String.valueOf(currentSalvage));
            this.script.capture(AnsiTable.getCode("white") + " > Gold Reset: " + AnsiTable.getCode("yellow") + String.valueOf(startGoldLoop));
            this.script.capture(AnsiTable.getCode("white") + " > Salvage Reset: " + AnsiTable.getCode("yellow") + String.valueOf(startSalvageLoop));
            this.script.capture(AnsiTable.getCode("white") + " > Recall to Trainer Speedwalk: " + AnsiTable.getCode("yellow") + speedwalkToTrainer);
            this.script.capture(AnsiTable.getCode("white") + " > Trainer to Salvage Speedwalk: " + AnsiTable.getCode("yellow") + speedwalkToSalvageGnome);
            this.script.capture(AnsiTable.getCode("white") + " > Script File Name: " + AnsiTable.getCode("yellow") + zoneToBeRan);
            this.script.capture(AnsiTable.getCode("white") + " > Recall to Zone Speedwalk: " + AnsiTable.getCode("yellow") + speedwalkToZone);
            this.script.capture(AnsiTable.getCode("white") + " > Temple to Zone Speedwalk: " + AnsiTable.getCode("yellow") + speedwalkToZoneFromTemple);
            this.script.capture(AnsiTable.getCode("white") + " > Training: " + AnsiTable.getCode("yellow") + trainingToBeDone);
            this.script.capture(AnsiTable.getCode("white") + " > PPM: " + AnsiTable.getCode("yellow") + ppm);
            this.script.capture(AnsiTable.getCode("white") + " > Duration: " + AnsiTable.getCode("yellow") + script.getVariable("UMC_RUNTIME"));
            this.script.capture(AnsiTable.getCode("light red") + "--------------------------------------------------------------------------\001");
        }

        if ("pause".equals(argArray.get(0))) {
            script.capture(AnsiTable.getCode("yellow") + "OcelLoop " + AnsiTable.getCode("light red") + "WARNING: " + AnsiTable.getCode("white") + "Stop the presses!\001");
            loopStatus = 2;
        }

        if ("resume".equals(argArray.get(0))) {
            readPropertiesFile();
            script.capture(AnsiTable.getCode("yellow") + "OcelLoop " + AnsiTable.getCode("light red") + "SUCCESS: " + AnsiTable.getCode("white") + "Enjoy the ride.\001");
            loopStatus = 0;
        }

        if ("xp".equals(argArray.get(0))) {
            loopingMode = 1;
        }

        if ("setsalvage".equals(argArray.get(0))) {
            if (argArray.size() == 1 || argArray.size() > 2) {
                script.capture(AnsiTable.getCode("yellow") + "OcelLoop " + AnsiTable.getCode("light red") + "WARNING: " + AnsiTable.getCode("white") + "No value given.");
            } else {
                currentSalvage = Integer.getInteger(argArray.get(1));
            }
        }

        if ("gold".equals(argArray.get(0))) {
            if (argArray.size() == 1 || argArray.size() > 2) {
                //Set a default gold looping value
                startGoldLoop = 980000000;
                loopingMode = 2;
            } else {
                startGoldLoop = Integer.valueOf(argArray.get(1));
                loopingMode = 2;
            }
        }

        if ("salvage".equals(argArray.get(0))) {
            if (argArray.size() == 1 || argArray.size() > 2) {
                startSalvageLoop = 5;
                loopingMode = 3;
            } else {
                startSalvageLoop = Integer.valueOf(argArray.get(1));
                loopingMode = 3;
            }
        }

        if ("reversetest".equals(argArray.get(0))) {
            if (!argArray.get(1).isEmpty()) {
                doReverseSpeedwalk(argArray.get(1));
            }
        }

        return "";
    }

    public void estimator() {
        currentGoldEstimate = 0;
        int second = 0;
        int minute = 0;
        int hour = 0;

        Matcher splitDuration = timeSplit.matcher(script.getVariable("UMC_RUNTIME"));

        splitDuration.reset();
        if (splitDuration.find()) {
            if (splitDuration.group(1) != null) {
                hour = Integer.parseInt(splitDuration.group(1));
            }
            if (splitDuration.group(2) != null) {
                minute = Integer.parseInt(splitDuration.group(2));
            }
            if (splitDuration.group(3) != null) {
                second = Integer.parseInt(splitDuration.group(3));
            }
        }

        if (second > 25 && minute >= 0 && hour >= 0 && script.getIntegerVariable("UMC_PPM") > ppm) {
            ppm = script.getIntegerVariable("UMC_PPM");
        }

        currentGoldEstimate = (((hour * 60) + minute) * ppm * 20000) + (int) (((double) second / 60) * ppm * 20000);

        script.setVariable("OCELLOOP_ESTIMATEDGOLD", String.valueOf(currentGoldEstimate));
    }

    public void readPropertiesFile() {
        Path p = Paths.get("OcelBot.properties");
        Properties prop = new Properties();
        if (Files.exists(p)) {
            try (InputStream fileInput = new FileInputStream("OcelBot.properties")) {
                prop.load(fileInput);
                fileInput.close();

                if (prop.containsKey("OcelLoop_speedwalkToTrainer")) {
                    if (prop.getProperty("OcelLoop_speedwalkToTrainer").length() > 0) {
                        speedwalkToTrainer = prop.getProperty("OcelLoop_speedwalkToTrainer");
                    }
                } else {
                    script.captureMatch(AnsiTable.getCode("light red") + "OcelLoop: Setting Trainer with Default Value - Outpost");
                    prop.setProperty("OcelLoop_speedwalkToTrainer", "none");
                }
                if (prop.containsKey("OcelLoop_speedwalkToSalvageGnome")) {
                    if (prop.getProperty("OcelLoop_speedwalkToSalvageGnome").length() > 0) {
                        speedwalkToSalvageGnome = prop.getProperty("OcelLoop_speedwalkToSalvageGnome");
                    }
                } else {
                    script.captureMatch(AnsiTable.getCode("light red") + "OcelLoop: Setting Gnome with Default Value - Outpost");
                    prop.setProperty("OcelLoop_speedwalkToSalvageGnome", "3s2e");
                }
                if (prop.containsKey("OcelLoop_speedwalkToZone")) {
                    if (prop.getProperty("OcelLoop_speedwalkToZone").length() > 0) {
                        speedwalkToZone = prop.getProperty("OcelLoop_speedwalkToZone");
                    }
                } else {
                    script.captureMatch(AnsiTable.getCode("light red") + "OcelLoop: Setting Speedwalk with Default Value - Outpost to WLC");
                    prop.setProperty("OcelLoop_speedwalkToZone", "12sen");
                }
                if (prop.containsKey("OcelLoop_zone")) {
                    if (prop.getProperty("OcelLoop_zone").length() > 0) {
                        zoneToBeRan = prop.getProperty("OcelLoop_zone");
                    }
                } else {
                    script.captureMatch(AnsiTable.getCode("light red") + "OcelLoop: Setting Zone with Default Value - WLC");
                    prop.setProperty("OcelLoop_zone", "wlc");
                }
                if (prop.containsKey("OcelLoop_training")) {
                    if (prop.getProperty("OcelLoop_training").length() > 0) {
                        trainingToBeDone = prop.getProperty("OcelLoop_training");
                    }
                } else {
                    script.captureMatch(AnsiTable.getCode("light red") + "OcelLoop: Setting Training with Default Value - conv 9 & conv 5");
                    prop.setProperty("OcelLoop_training", "conv 9;conv 5");
                }
                if (prop.containsKey("OcelLoop_speedwalkToZoneFromTemple")) {
                    if (prop.getProperty("OcelLoop_speedwalkToZoneFromTemple").length() > 0) {
                        speedwalkToZoneFromTemple = prop.getProperty("OcelLoop_speedwalkToZoneFromTemple");
                    }
                } else {
                    script.captureMatch(AnsiTable.getCode("light red") + "OcelLoop: Setting Temple PK Speedwalk with Default Value - Temple to WLC");
                    prop.setProperty("OcelLoop_speedwalkToZoneFromTemple", "2n;open gate;2n10n30e27n14esw");
                }

                try (OutputStream fileOutput = new FileOutputStream("OcelBot.properties")) {
                    prop.store(fileOutput, "OcelBot Properties - For lists seperate with a comma");
                    fileOutput.close();
                }
            } catch (IOException e) {
                script.captureMatch(AnsiTable.getCode("light red") + "OcelBot: Error - unable to create OcelBot.properties file.");
            } finally {

            }
        } else {
            try {
                Files.createFile(p);
                try (OutputStream fileOutput = new FileOutputStream("OcelBot.properties")) {
                    prop.setProperty("OcelLoop_speedwalkToTrainer", "none");
                    prop.setProperty("OcelLoop_speedwalkToSalvageGnome", "3s2e");
                    prop.setProperty("OcelLoop_zone", "wlc");
                    prop.setProperty("OcelLoop_speedwalkToZone", "12sen");
                    prop.setProperty("OcelLoop_training", "conv 9;conv 5");
                    prop.setProperty("OcelLoop_speedwalkToZoneFromTemple", "2n;open gate;2n10n30e27n14esw");

                    prop.store(fileOutput, "OcelBot Properties - For lists seperate with a comma");
                    fileOutput.close();
                }
            } catch (IOException e) {
                script.captureMatch(AnsiTable.getCode("light red") + "OcelBot: Error - unable to create OcelBot.properties file.");
            }
            script.captureMatch(p.toAbsolutePath().toString());
            script.captureMatch(AnsiTable.getCode("light red") + "OcelBot: Error - unable to load OcelBot.properties file.");
        }
    }

    public void IncomingEvent(ScriptInterface event) {
        Matcher outOfCombat = nonCombatPrompt.matcher(event.getText());
        Matcher inCombat = combatPrompt.matcher(event.getText());
        Matcher maxXP = xpCapPrompt.matcher(event.getEvent());
        Matcher maxGold = goldCap.matcher(event.getEvent());
        Matcher getSalvageReceive = receiveSalvage.matcher(event.getText());
        Matcher getSalvageGiven = givenSalvage.matcher(event.getText());
        Matcher loseSalvageGiven = giveSalvage.matcher(event.getText());
        Matcher loseSalvageWither = witherSalvage.matcher(event.getText());
        Matcher loseSalvageDropped = droppedSalvage.matcher(event.getText());
        Matcher movingRooms = movedRooms.matcher(event.getText());
        Matcher pk = pkFlag.matcher(event.getEvent());

        while (inSpeedwalk) {
            if (movingRooms.matches()) {
                inSpeedwalk = true;
            } else if (script.getText().matches("Alas, you cannot go in that direction.")) {
                inSpeedwalk = false;
            } else if (inCombat.find()) {
                inSpeedwalk = false;
            }

            if (!inSpeedwalk) {
                //Jumping out of speedwalk ungracefully?
            }
        }

        if (event.getText().matches("^A scrounger gnome stands here\\.")) {
            gnomeRoom = script.getVariable("UMC_ROOM");
        }

        //We're at a gnome!
        if (event.getText().matches("^A scrounger gnome stands here\\.") && loopStatus == 3 && currentSalvage > 0) {
            script.send("follow gnome");
        }

        if (event.getText().matches("^There's no one here by that name\\.") && loopStatus == 3 && currentSalvage > 0) {
            script.capture(AnsiTable.getCode("yellow") + "OcelLoop [Salvage] " + AnsiTable.getCode("light red") + "WARNING: " + AnsiTable.getCode("white") + "No salvage gnome detected!\001");
            doReverseSpeedwalk(speedwalkToSalvageGnome);
            doZone();
        }

        //A scrounger gnome says, 'At your service, Ocellaris!'
        if (event.getText().matches("^A scrounger gnome says, 'At your service, " + script.getVariable("UMC_NAME") + "!'") && loopStatus == 3 && currentSalvage > 0) {
            if (currentSalvage <= 5) {
                for (int i = 0; i < currentSalvage; i++) {
                    script.send("give salvage, gnome");
                }
                script.send("answer begin");
            } else {
                for (int i = 0; i < 5; i++) {
                    script.send("give salvage, gnome");
                }
                script.send("answer begin");
            }
            gettingSalvageAtGnome = true;
        }

        if (event.getText().matches("^A scrounger gnome says, 'No valid salvages were received - our time is valuable, please do not waste it.'") && loopStatus == 3 && gettingSalvageAtGnome == true) {
            doReverseSpeedwalk(speedwalkToSalvageGnome);
            currentSalvage = 0;
            doZone();
        }

        if (event.getText().matches("^A scrounger gnome says, 'Thank you for your business, customer " + script.getVariable("UMC_NAME") + "!'") && loopStatus == 3 && currentSalvage > 0) {
            script.send("follow gnome");
        } else if (event.getText().matches("^A scrounger gnome says, 'Thank you for your business, customer " + script.getVariable("UMC_NAME") + "!'") && loopStatus == 3 && currentSalvage == 0) {
            if (speedwalkToSalvageGnome != null || speedwalkToSalvageGnome.isEmpty()) {
                doReverseSpeedwalk(speedwalkToSalvageGnome);
                currentSalvage = 0;
                doZone();
            }
        }

        if (event.getText().matches("^You say, 'OcelLoop begin\\.'")) {
            if (zoneToBeRan != null || zoneToBeRan.isEmpty()) {
                script.parse("#load " + zoneToBeRan);
                script.parse("#begin");
                goldModeSelector = 0;
                xpModeSelector = 0;
                currentGoldEstimate = 0;
            } else {
                //error message about sucking
            }
        }

        if (event.getText().matches("^Note: To train a stat type: 'train <statistic>'") && loopStatus == 3 && atTrainer) {
            script.parse(trainingToBeDone);
            atTrainer = false;
            doSalvage();
        }

        if (event.getText().matches("^You need a trainer to increase your stats\\.") && loopStatus == 3 && atTrainer == true) {
            script.capture(AnsiTable.getCode("yellow") + "OcelLoop [Training] " + AnsiTable.getCode("light red") + "WARNING: " + AnsiTable.getCode("white") + "No trainer detected!\001");
            startGoldLoop = 500000000;
            loopingMode = 2;
            atTrainer = false;
            doSalvage();
        }

        maxXP.reset();
        if (maxXP.find()) {
            currentXP = Integer.parseInt(maxXP.group(1));
            script.setVariable("OCELLOOP_CURRENTXP", maxXP.group(1));
        }

        maxGold.reset();
        if (maxGold.find()) {
            currentGold = Integer.parseInt(maxGold.group(1));
            script.setVariable("OCELLOOP_CURRENTGOLD", maxGold.group(1));
        } else {
            estimator();
        }

        /*If we get more than 2 salvage it will show up as a digit in our regex and
         we can add that amount to current salvage, otherwise only add 1.
         */
        if (getSalvageReceive.find() || getSalvageGiven.find()) {
            if (getSalvageReceive.group(1) == null) {
                currentSalvage++;
            } else if (getSalvageGiven.group(1) == null) {
                currentSalvage++;
            } else if (getSalvageGiven.group(1) != null) {
                currentSalvage += Integer.parseInt(getSalvageReceive.group(1));
            } else if (getSalvageReceive.group(1) != null) {
                currentSalvage += Integer.parseInt(getSalvageReceive.group(1));
            }
            script.setVariable("OCELLOOP_SALVAGE", String.valueOf(currentSalvage));
        }

        getSalvageReceive.reset();
        getSalvageGiven.reset();

        if (loseSalvageGiven.find() || loseSalvageWither.find() || loseSalvageDropped.find()) {
            if (loseSalvageGiven.group(1) == null) {
                currentSalvage--;
            } else if (loseSalvageGiven.group(1) != null) {
                currentSalvage -= Integer.parseInt(loseSalvageGiven.group(1));
            } else if (loseSalvageWither.group(1) == null) {
                currentSalvage--;
            } else if (loseSalvageWither.group(1) != null) {
                currentSalvage -= Integer.parseInt(loseSalvageWither.group(1));
            } else if (loseSalvageDropped.group(1) == null) {
                currentSalvage--;
            } else if (loseSalvageDropped.group(1) != null) {
                currentSalvage -= Integer.parseInt(loseSalvageWither.group(1));
            }
            if (currentSalvage < 0) {
                currentSalvage = 0;
            }
            script.setVariable("OCELLOOP_SALVAGE", String.valueOf(currentSalvage));
        }

        loseSalvageGiven.reset();
        loseSalvageWither.reset();
        loseSalvageDropped.reset();

        /*
         0 = check
         1 = wait until out of combat and do loop
         2 = ignore
         3 = doing loop
         */
        if (loopStatus == 0) {
            /*
             0 = off
             1 = xp
             2 = gold
             3 = salvage
             */

            if (currentGoldEstimate > 980000000 || currentGold > 980000000) {
                script.capture(AnsiTable.getCode("yellow") + "OcelLoop [Gold - Auto] " + AnsiTable.getCode("light red") + "WARNING: " + AnsiTable.getCode("white") + "Auto looping initiated due to gold cap!\001");
                loopStatus = 1;
            }
            if (loopingMode == 1) {
                if (currentXP == 2000000000 && xpModeSelector == 0) {
                    xpModeSelector = 1;
                    script.capture(AnsiTable.getCode("yellow") + "OcelLoop [XP - Prompt] " + AnsiTable.getCode("light red") + "NOTICE: " + AnsiTable.getCode("white") + "Executing auto looping.\001");
                    loopStatus = 1;

                }
                if ("You can learn no more.".equals(event.getText()) && xpModeSelector == 0) {
                    xpModeSelector = 1;
                    script.capture(AnsiTable.getCode("yellow") + "OcelLoop [XP - Auto] " + AnsiTable.getCode("light red") + "NOTICE: " + AnsiTable.getCode("white") + "Executing auto looping.\001");
                    loopStatus = 1;
                }
            }

            if (loopingMode == 2) {
                if (currentGold >= startGoldLoop && goldModeSelector == 0) {
                    goldModeSelector = 1;
                    script.capture(AnsiTable.getCode("yellow") + "OcelLoop [Gold - Prompt] " + AnsiTable.getCode("light red") + "NOTICE: " + AnsiTable.getCode("white") + "Executing auto looping.\001");
                    loopStatus = 1;
                }
                if (currentGoldEstimate > startGoldLoop && goldModeSelector == 0) {
                    goldModeSelector = 1;
                    script.capture(AnsiTable.getCode("yellow") + "OcelLoop [Gold - Estimate] " + AnsiTable.getCode("light red") + "NOTICE: " + AnsiTable.getCode("white") + "Executing auto looping.\001");
                    loopStatus = 1;
                }
            }

            if (loopingMode == 3) {
                if (currentSalvage >= startSalvageLoop) {
                    script.capture(AnsiTable.getCode("yellow") + "OcelLoop [Salvage - Counter] " + AnsiTable.getCode("light red") + "NOTICE: " + AnsiTable.getCode("white") + "Executing auto looping.\001");
                    loopStatus = 1;
                }
                //Wither protection here
            }

            //Add in if pkd
        }

        if (outOfCombat.find() && loopStatus == 1) {
            if (pk.find()) {
                speedwalkToTrainer = "w";
                speedwalkToSalvageGnome = "ene";
                speedwalkToZone = "3n" + speedwalkToZoneFromTemple;
            }
            loopStatus = 3;
            doLoop();
            pk.reset();
        }
        outOfCombat.reset();

    }

    public void doLoop() {
        //We're going home!
        script.parse("#end");
        script.send("clear");
        script.setVariable("UMC_RUNTIME", "0s");
        script.send("recall follower_of_" + script.getVariable("UMC_NAME"));
        script.send("recall follower_of_" + script.getVariable("UMC_NAME"));
        script.send("recall");

        //Always training!
        //Add in combat speedwalk protection, trees, warp/summon
        if (speedwalkToTrainer != null || speedwalkToTrainer.isEmpty() || !"none".equals(speedwalkToTrainer)) {
            script.parse(speedwalkToTrainer);
        }

        doTrain();
    }

    public void doTrain() {
        script.send("train");
        atTrainer = true;
    }

    public void doSalvage() {
        if (currentSalvage > 0) {
            if (speedwalkToSalvageGnome != null || speedwalkToSalvageGnome.isEmpty()) {
                script.parse(speedwalkToSalvageGnome);
            }
        } else {
            doZone();
        }
    }

    public void doZone() {
        if (speedwalkToTrainer != null || speedwalkToTrainer.isEmpty() || !"none".equals(speedwalkToTrainer)) {
            doReverseSpeedwalk(speedwalkToTrainer);
        }

        if (speedwalkToZone != null || speedwalkToZone.isEmpty()) {
            script.parse(speedwalkToZone);
            script.parse("say OcelLoop begin.");
            readPropertiesFile();
        } else {
            //error message about sucking
        }

        ppm = 0;
        loopStatus = 0;
    }

    public void checkMovement() {

    }

    public void doReverseSpeedwalk(String speedwalk) {
        inSpeedwalk = true;
        for (String s : getReverseSpeedwalk(speedwalk)) {
            if (inSpeedwalk) {
                script.send(s);
            } else {
                script.send("clear");
                script.capture(AnsiTable.getCode("yellow") + "OcelLoop " + AnsiTable.getCode("light red") + "WARNING: " + AnsiTable.getCode("white") + "Invalid speedwalk.\001");
            }
        }
        inSpeedwalk = false;
    }

    public LinkedList<String> getReverseSpeedwalk(String speedwalk) {
        String[] splitOnSemiColon = speedwalk.split("\\;");
        String[] splitOnNumber;
        LinkedList<String> tempSpeedwalkNonReversedInternal = new LinkedList<>();
        LinkedList<String> tempSpeedwalkReversedAll = new LinkedList<>();
        LinkedList<String> speedwalkReversed = new LinkedList<>();

        int str = 0;
        for (String s : splitOnSemiColon) {
            if (s.toLowerCase().contains("open")) {
                splitOnSemiColon[str] = s.toLowerCase().replaceAll("open", "close");
            } else if (s.toLowerCase().contains("close")) {
                splitOnSemiColon[str] = s.toLowerCase().replaceAll("close", "open");
            } else if (s.toLowerCase().contains("unlock")) {
                splitOnSemiColon[str] = s.toLowerCase().replaceAll("unlock", "lock");
            } else if (s.toLowerCase().contains("lock")) {
                splitOnSemiColon[str] = s.toLowerCase().replaceAll("lock", "unlock");
            }
            str++;
            s = s.trim();
        }

        for (String s : splitOnSemiColon) {
            splitOnNumber = s.trim().split("(?<=[ a-zA-Z])(?=\\d)|(?<=\\d)(?=[  a-zA-Z])");
            tempSpeedwalkNonReversedInternal.addAll(Arrays.asList(splitOnNumber));
        }

        Collections.reverse(tempSpeedwalkNonReversedInternal);

        for (String s : tempSpeedwalkNonReversedInternal) {
            if (s.matches(".*\\d+.*") || s.matches("(?:open|close|lock|unlock).*")) {
                tempSpeedwalkReversedAll.add(s);
            } else {
                StringBuilder sb = new StringBuilder();
                s = s.replaceAll(" ", "");
                List<Character> partialSpeedwalk = s.chars().mapToObj(c -> (char) c).collect(Collectors.toList());
                Collections.reverse(partialSpeedwalk);
                for (char c : partialSpeedwalk) {
                    if (c == 'e') {
                        c = 'w';
                    } else if (c == 'w') {
                        c = 'e';
                    } else if (c == 'n') {
                        c = 's';
                    } else if (c == 's') {
                        c = 'n';
                    } else if (c == 'u') {
                        c = 'd';
                    } else if (c == 'd') {
                        c = 'u';
                    }
                    tempSpeedwalkReversedAll.add(String.valueOf(c));
                }
            }
        }

        String curr = null;
        for (String next : tempSpeedwalkReversedAll) {
            if (curr != null) {
                if (next.matches(".*\\d+.*")) {
                    for (int i = 1; i < Integer.valueOf(next); i++) {
                        speedwalkReversed.add(curr);
                    }
                } else if (next.matches("(?:open|close|lock|unlock).*")) {
                    speedwalkReversed.add(next);
                    continue;
                } else {
                    speedwalkReversed.add(next);
                    //System.out.println(next);
                }
            } else {
                speedwalkReversed.add(next);
            }
            curr = next;
        }

        return speedwalkReversed;
    }
}
