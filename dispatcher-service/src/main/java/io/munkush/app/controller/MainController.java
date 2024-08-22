package io.munkush.app.controller;

import feign.FeignException;
import io.munkush.app.service.ClickerServiceClient;
import io.munkush.app.service.GeneratorServiceClient;
import io.munkush.app.service.UserServiceClient;
import io.munkush.app.service.dto.ClickRequest;
import io.munkush.app.service.dto.UserCreateRequest;
import io.munkush.app.service.dto.UserLoginRequest;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.Arrays;
import java.util.Objects;

@Controller
@RequiredArgsConstructor
public class MainController {

    private final GeneratorServiceClient generatorServiceClient;
    private final UserServiceClient userServiceClient;
    private final ClickerServiceClient clickerServiceClient;


    @GetMapping("/")
    public String main(HttpServletRequest request, Model model){


        var token = getAccessTokenIfValid(request);

        if(token == null){
            return "login";
        } else {
            var username = getUsername(token);
            model.addAttribute("login", username);

            setClicks(model, username);
            setCodes(model);

            return "table";
        }

    }





    @GetMapping("/login")
    public String login(){
        return "login";
    }

    @GetMapping("/registration")
    public String registration(){
        return "registration";
    }


    @PostMapping("/generate")
    public String generate(@RequestParam("login") String login,
                           Model model){

        var code = generatorServiceClient.generate(login);
        UserCreateRequest userCreateRequest = new UserCreateRequest(login, code);

        try {
            userServiceClient.save(userCreateRequest).getBody();
        } catch (FeignException e){
            model.addAttribute("error", e.getLocalizedMessage());
            return "registration";
        }


        model.addAttribute("login", login);
        model.addAttribute("code", code);

        return "registration";
    }

    @PostMapping("/login")
    public String login(@RequestParam("login") String login,
                        @RequestParam("code") String code,
                        Model model,
                        HttpServletResponse servletResponse){

        UserLoginRequest request = new UserLoginRequest(login, code);

        try {
            var response = userServiceClient.login(request);
            if (response.getStatusCode().is2xxSuccessful()) {
                model.addAttribute("login", login);

                var accessToken = Objects.requireNonNull(response.getBody()).accessToken();
                Cookie accessCookie = new Cookie("access-token",
                        accessToken);
                Cookie refreshCookie = new Cookie("refresh-token",
                        Objects.requireNonNull(response.getBody()).refreshToken());

                accessCookie.setHttpOnly(true);
                accessCookie.setMaxAge(5 * 60 * 60);
                accessCookie.setPath("/");

                refreshCookie.setHttpOnly(true);
                refreshCookie.setMaxAge(5 * 60 * 60);
                refreshCookie.setPath("/");

                servletResponse.addCookie(accessCookie);
                servletResponse.addCookie(refreshCookie);

                setClicks(model, getUsername(accessToken));
                setCodes(model);

                return "table";
            }
        } catch (FeignException e) {

            model.addAttribute("error", e.getLocalizedMessage());

            return "login";
        }





        return "table";
    }

    @GetMapping("/logout")
    public String logout(HttpServletResponse response,
                         Model model){


        Cookie accessCookie = new Cookie("access-token", null);
        accessCookie.setHttpOnly(true);
        accessCookie.setMaxAge(0);
        accessCookie.setPath("/");

        Cookie refreshCookie = new Cookie("refresh-token", null);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setMaxAge(0);
        refreshCookie.setPath("/");

        response.addCookie(accessCookie);
        response.addCookie(refreshCookie);


        model.addAttribute("msg", "Successful logout");

        return "login";
    }


    @PostMapping("/saveCoordinate")
    public String save(
            @RequestParam String x,
            @RequestParam String y,
            HttpServletRequest request,
            Model model
    ){

        var token = getAccessTokenIfValid(request);

        String username = null;

        if(token != null){

            username = getUsername(token);
            ClickRequest clickRequest = new ClickRequest(x, y, username);
            clickerServiceClient.save(clickRequest);

        }

        setClicks(model, username);
        setCodes(model);

        return "table";
    }


    private String getAccessTokenIfValid(HttpServletRequest request){
        var cookies = request.getCookies();

        var cookie = Arrays.stream(cookies).filter(r -> r.getName().equals("access-token")).findFirst();

        if (cookie.isEmpty()) {
            return null;
        } else {

            var token = cookie.get().getValue();

            boolean isTokenValid;

            try {
                isTokenValid = Boolean.TRUE.equals(userServiceClient.isTokenValid(token).getBody());
            } catch (Exception e){
                return null;
            }
            if (isTokenValid) {
                return token;
            }
        }

        return null;
    }

    private String getUsername(String token){
        try {
            return userServiceClient.getUsername(token).getBody();
        } catch (Exception e){
            return null;
        }
    }

    private void setClicks(Model model, String username){
        var clicks = clickerServiceClient.getAll(username).getBody();

        model.addAttribute("clicks", clicks);
    }

    private void setCodes(Model model){

        model.addAttribute("codes", userServiceClient.getAll().getBody());
    }


}
