package ru.mai;

import com.pullenti.morph.*;
import com.pullenti.ner.core.MiscHelper;
import com.pullenti.ner.geo.internal.MiscLocationHelper;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

    public static void main(String[] args) throws Exception {
        com.pullenti.unisharp.Stopwatch sw = com.pullenti.unisharp.Utils.startNewStopwatch();
        // инициализация - необходимо проводить один раз до обработки текстов
        System.out.print("Initializing SDK Pullenti ver " + com.pullenti.Sdk.getVersion() + " (" + com.pullenti.Sdk.getVersionDate() + ") ... ");
        // инициализируются движок и все имеющиеся анализаторы
        com.pullenti.Sdk.initializeAll();
        sw.stop();
        System.out.println("OK (by " + ((int) sw.getElapsedMilliseconds()) + " ms), version " + com.pullenti.ner.ProcessorService.getVersion());

        HashMap<String, String> tags = new HashMap<>();

        fillTags(tags);

        ArrayList<String> texts = new ArrayList<>();

        Files.walk(Paths.get("D:\\Downloads\\RNC_million\\RNC_million\\sample_ar\\TEXTS"))
                .filter(Files::isRegularFile)
                .forEach((file -> {
                    texts.add(file.toString());
                }));

        AtomicInteger intUnfamilliar = new AtomicInteger(); // ненайденные в словаре
        AtomicInteger intKnown = new AtomicInteger(); // найденные в словаре
        AtomicInteger wordCount = new AtomicInteger(); // суммарное количесвто слов
        AtomicInteger accuracy = new AtomicInteger(); // точно определённые слова
        AtomicInteger morphAccuracy = new AtomicInteger(); // первая же форма с подходящими морфологическими хар-ками
        AtomicBoolean isAdded = new AtomicBoolean(false);

        Instant start;
        Instant finish;
        long elapsed = 0;

        try {
            for (String text : texts) {
                System.out.println("next file" + text);
                DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                Document document = documentBuilder.parse(text);

                Node html = document.getDocumentElement();

                NodeList htmlProps = html.getChildNodes();
                for (int i = 0; i < htmlProps.getLength(); i++) {
                    Node body = htmlProps.item(i);
                    if (body.getNodeType() != Node.TEXT_NODE && body.getNodeName().equals("body")) {
                        NodeList bodyProps = body.getChildNodes();
                        for (int j = 0; j < bodyProps.getLength(); j++) {
                            Node paragraph = bodyProps.item(j);
                            if (paragraph.getNodeType() != Node.TEXT_NODE && (paragraph.getNodeName().equals("p") || paragraph.getNodeName().equals("speach"))) {
                                NodeList paragraphProps = paragraph.getChildNodes();
                                for (int k = 0; k < paragraphProps.getLength(); k++) {
                                    Node sentence = paragraphProps.item(k);
                                    if (sentence.getNodeType() != Node.TEXT_NODE && sentence.getNodeName().equals("se")) {
                                        NodeList sentenceProps = sentence.getChildNodes();
                                        for (int m = 0; m < sentenceProps.getLength(); m++) {
                                            Node word = sentenceProps.item(m);
                                            if (word.getNodeType() != Node.TEXT_NODE && word.getNodeName().equals("w")) {
                                                wordCount.getAndIncrement();
                                                NodeList wordProps = word.getChildNodes();
                                                start = Instant.now();
                                                for (int n = 0; n < wordProps.getLength(); n++) {
                                                    Node characteristics = wordProps.item(n);
                                                    if (isAdded.get()) {
                                                        continue;
                                                    }
                                                    if (characteristics.getNodeType() != Node.TEXT_NODE && characteristics.getNodeName().equals("ana")) {
                                                        ArrayList<MorphToken> mor = MorphologyService.process(word.getTextContent().toLowerCase(Locale.ROOT).replaceAll("[` ]", ""), MorphLang.RU, null);
                                                        assert mor != null;
                                                        MorphToken token = mor.get(0);
                                                        ArrayList<MorphWordForm> forms = token.wordForms;
                                                        if (forms.size() == 0) {
                                                            intUnfamilliar.getAndIncrement();
                                                        } else {
                                                            if (forms.get(0).isInDictionary()) {
                                                                intKnown.getAndIncrement();
                                                                if (token.getLemma().toLowerCase(Locale.ROOT).equals(characteristics.getAttributes().getNamedItem("lex").getNodeValue().toLowerCase(Locale.ROOT).replaceAll("ё", "е"))) {
                                                                    accuracy.getAndIncrement();
                                                                }

                                                                isAdded.set(true);

                                                                MorphBaseInfo info = MorphologyService.getWordBaseInfo(word.getTextContent().toLowerCase(Locale.ROOT).replaceAll("[` ]", ""), MorphLang.RU, false, true);

                                                                String transformTag = info.toString().replaceAll("1 л", "1л")
                                                                        .replaceAll("2 л", "2л").replaceAll("3 л", "3л")
                                                                        .replaceAll(" ", ",");

                                                                String[] transformTagSplit = transformTag.split("[,]");
                                                                StringBuilder temp = new StringBuilder();

                                                                for (String value : transformTagSplit) {
                                                                    if (value.contains("|")) {
                                                                        String[] tempTag = value.split("\\|");
                                                                        temp.append(tags.get(tempTag[0]));
                                                                    } else {
                                                                        temp.append(tags.get(value));
                                                                    }
                                                                    temp.append(",");
                                                                }

                                                                temp = new StringBuilder(temp.substring(0, temp.length() - 1));

                                                                if (!transformTag.contains("к.ф.")) {
                                                                    temp.append(",plen");
                                                                }

                                                                String[] transformedTag = temp.toString().split(",");

                                                                List<String> list = new ArrayList<>();
                                                                for (String s : transformedTag) {
                                                                    if (s != null && !Objects.equals(s, "null") && !s.equals("0") && s.length() > 0) {
                                                                        list.add(s);
                                                                    }
                                                                }
                                                                transformedTag = list.toArray(new String[0]);

                                                                String[] markTags = characteristics.getAttributes().getNamedItem("gr").getNodeValue()
                                                                        //.replaceAll("-PRO", "").replaceAll("PRO", "")
                                                                        .replaceAll("distort", "").replaceAll("persn", "")
                                                                        .replaceAll("patrn", "").replaceAll("indic", "")
                                                                        .replaceAll("imper", "").replaceAll("abbr", "")
                                                                        .replaceAll("ciph", "").replaceAll("INIT", "")
                                                                        .replaceAll("anom", "").replaceAll("famn", "")
                                                                        .replaceAll("zoon", "").replaceAll("pass", "")
                                                                        .replaceAll("inan", "").replaceAll("anim", "")
                                                                        .replaceAll("intr", "").replaceAll("tran", "")
                                                                        .replaceAll("act", "").replaceAll("ipf", "")
                                                                        .replaceAll("med", "").replaceAll("pf", "")
                                                                        .split("[,=]");

                                                                list = new ArrayList<>();
                                                                for (String s : markTags) {
                                                                    if (s != null && !Objects.equals(s, "null") && !s.equals("0") && s.length() > 0) {
                                                                        list.add(s);
                                                                    }
                                                                }
                                                                markTags = list.toArray(new String[0]);

                                                                for (String markTag : markTags) {
                                                                    if (!Arrays.asList(transformedTag).contains(markTag)) {
                                                                        isAdded.set(false);
                                                                    }
                                                                }

                                                                if (isAdded.get()){
                                                                    morphAccuracy.getAndIncrement();
                                                                }

                                                            } else {
                                                                intUnfamilliar.getAndIncrement();
                                                            }
                                                        }
                                                        isAdded.set(true);
                                                    }
                                                }
                                                finish = Instant.now();
                                                elapsed += Duration.between(start, finish).toMillis();
                                                isAdded.set(false);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            System.out.println("Количество ненайдённых: " + intUnfamilliar);
            System.out.println("Количество найдённых в словаре: " + intKnown);
            System.out.println("Общее количество слов: " + wordCount);
            System.out.println("Точно определенных начальных форм слов: " + accuracy);
            System.out.println("Точно определенных форм слов с полными характеристиками: " + morphAccuracy);
            System.out.println("Процент ненайдённых:" + intUnfamilliar.doubleValue()/wordCount.doubleValue());
            System.out.println("Точность начальных форм: " + accuracy.doubleValue()/intKnown.doubleValue());
            System.out.println("Точность определения характеристик первой формы: " + morphAccuracy.doubleValue()/intKnown.doubleValue());
            System.out.println("Затраченное время: " + (double)elapsed/1000 + " секунд");

        } catch (ParserConfigurationException | SAXException | IOException ex) {
            ex.printStackTrace(System.out);
        }
    }
    static void fillTags(HashMap<String, String> tags) {
        tags.put("существ.", "S");
        tags.put("прилаг.", "A");
        tags.put("глагол", "V");
        tags.put("наречие", "ADV");
        tags.put("предлог","PR");
        tags.put("союз","CONJ");
        tags.put("ед.ч.","sg");
        tags.put("мн.ч.", "pl");
        tags.put("муж.р.", "m");
        tags.put("жен.р.", "f");
        tags.put("ср.р.", "n");
        tags.put("именит.", "nom");
        tags.put("родит.", "gen");
        tags.put("дател.","dat");
        tags.put("винит.", "acc");
        tags.put("творит.", "ins");
        tags.put("предлож.", "loc");
        tags.put("зват.", "voc");
        tags.put("п.вр.", "praet");
        tags.put("н.вр.", "praes");
        tags.put("б.вр.","fut");
        tags.put("сов.в.","pf");
        tags.put("нес.в.","ipf");
        tags.put("1л.","1p");
        tags.put("2л.","2p");
        tags.put("3л.","3p");
        tags.put("к.ф.", "brev");
        tags.put("местоим.", "SPRO,APRO,ADVPRO,PRAEDICPRO");
    }
}
