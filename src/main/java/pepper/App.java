package pepper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.Part;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@SpringBootApplication
@Controller
public class App {
    static enum State {
        RECEIVED,
        ACCEPTED,
        DENIED,
        NOT_FOUND
    }

    private static final Logger logger = LoggerFactory.getLogger(App.class);

    static class Data {
        final String id;
        final byte[] data;
        final LocalDateTime dateTime;
        State state;

        public Data(String id, byte[] data, LocalDateTime dateTime, State state) {
            this.id = id;
            this.data = data;
            this.dateTime = dateTime;
            this.state = state;
        }

        static final Data NOT_FOUND = new Data(null, null, null, State.NOT_FOUND);

        public String getId() {
            return id;
        }

        public State getState() {
            return state;
        }

        public LocalDateTime getDateTime() {
            return dateTime;
        }
    }

    ConcurrentHashMap<String, Data> dataMap = new ConcurrentHashMap<>();
    @Autowired
    MailSender mailSender;
    @Value("${server.port:8080}")
    int port;

    @ResponseBody
    @RequestMapping(value = "receive")
    String receive(@RequestParam Part file) throws IOException {
        byte[] bytes = FileCopyUtils.copyToByteArray(file.getInputStream());
        String id = UUID.randomUUID().toString();
        dataMap.putIfAbsent(id, new Data(id, bytes, LocalDateTime.now(), State.RECEIVED));
        logger.info("received {}", id);
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setSubject("誰か来ました");
        mailMessage.setTo("メールアドレス1");
        mailMessage.setCc("メールアドレス2");
        mailMessage.setText("http://192.168.3.62:" + port + "/detail/" + id);
        mailSender.send(mailMessage);
        return id;
    }

    @ResponseBody
    @RequestMapping(value = "state/{id}", method = RequestMethod.GET)
    String state(@PathVariable String id) {
        String state = dataMap.getOrDefault(id, Data.NOT_FOUND).state.name();
        logger.info("state {} -> {}", id, state);
        return state;
    }

    @ResponseBody
    @RequestMapping(value = "image/{id}", method = RequestMethod.GET, produces = "image/jpg")
    byte[] image(@PathVariable String id) {
        return dataMap.getOrDefault(id, Data.NOT_FOUND).data;
    }

    @RequestMapping(value = "list", method = RequestMethod.GET)
    String list(Model model) {
        List<Data> dataList = dataMap.values().stream()
                .filter(d -> d.getState() == State.RECEIVED)
                .collect(Collectors.toList());
        model.addAttribute("dataList", dataList);
        return "list";
    }


    @RequestMapping(value = "detail/{id}", method = RequestMethod.GET)
    String list(@PathVariable String id, Model model) {
        Data data = dataMap.get(id);
        model.addAttribute("data", data);
        return "detail";
    }

    @RequestMapping(value = "reply", params = "accept", method = RequestMethod.POST)
    String accept(@RequestParam String id, RedirectAttributes attributes) {
        dataMap.get(id).state = State.ACCEPTED;
        attributes.addFlashAttribute("message", "受け取りました");
        return "redirect:/list";
    }


    @RequestMapping(value = "reply", params = "deny", method = RequestMethod.POST)
    String deny(@RequestParam String id, RedirectAttributes attributes) {
        dataMap.get(id).state = State.DENIED;
        attributes.addFlashAttribute("message", "拒否しました");
        return "redirect:/list";
    }

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}