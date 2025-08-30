package cn.wubo.entity.sql;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

@RestController
@RequestMapping(value = "test")
public class DemoController {

    @Autowired
    DemoService demoService;

    @GetMapping(value = "lock")
    public String lock(@RequestParam(name = "key") String key) {
        IntStream.range(0, 10).forEach(i -> CompletableFuture.runAsync(() -> demoService.doWork1(key)));
        //IntStream.range(0, 10).forEach(i -> CompletableFuture.runAsync(() -> demoService.doWork2(key)));
        //IntStream.range(0, 10).forEach(i -> CompletableFuture.runAsync(() -> demoService.doWork3(key)));
        //IntStream.range(0, 10).forEach(i -> CompletableFuture.runAsync(() -> demoService.doWork4(key)));
        return "success";
    }

}
