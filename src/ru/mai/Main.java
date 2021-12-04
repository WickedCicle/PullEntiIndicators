package ru.mai;

import com.pullenti.morph.MorphLang;
import com.pullenti.morph.MorphToken;
import com.pullenti.morph.MorphWordForm;
import com.pullenti.morph.MorphologyService;
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

    public static void main(String[] args) throws Exception, java.io.IOException {
        com.pullenti.unisharp.Stopwatch sw = com.pullenti.unisharp.Utils.startNewStopwatch();
        // инициализация - необходимо проводить один раз до обработки текстов
        System.out.print("Initializing SDK Pullenti ver " + com.pullenti.Sdk.getVersion() + " (" + com.pullenti.Sdk.getVersionDate() + ") ... ");
        // инициализируются движок и все имеющиеся анализаторы
        com.pullenti.Sdk.initializeAll();
        sw.stop();
        System.out.println("OK (by " + ((int) sw.getElapsedMilliseconds()) + " ms), version " + com.pullenti.ner.ProcessorService.getVersion());
        // посмотрим, какие анализаторы доступны

        ArrayList<String> texts = new ArrayList<>();

        Files.walk(Paths.get("D:\\Downloads\\RNC_million\\RNC_million\\sample_ar\\TEXTS"))
                .filter(Files::isRegularFile)
                .forEach((file -> {
                    texts.add(file.toString());
                }));

        AtomicInteger intUnfamilliar = new AtomicInteger();
        AtomicInteger intKnown = new AtomicInteger();
        AtomicInteger wordCount = new AtomicInteger();
        AtomicInteger accuracy = new AtomicInteger();
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
                                                start = Instant.now();;
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
            System.out.println("Процент ненайдённых:" + intUnfamilliar.doubleValue() / wordCount.doubleValue());
            System.out.println("Точность: " + accuracy.doubleValue() / intKnown.doubleValue());
            System.out.println("Затраченное время: " + (double)elapsed/1000 + " секунд");

        } catch (ParserConfigurationException | SAXException | IOException ex) {
            ex.printStackTrace(System.out);
        }

        System.out.println("Over!");
    }
}
