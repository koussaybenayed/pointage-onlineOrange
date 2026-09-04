package com.pointage.config;

import com.pointage.user.User;
import com.pointage.user.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(1)
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.findByEmail("admin@pointage.tn").isEmpty()) {
            User admin = new User();
            admin.setFullName("Administrateur");
            admin.setEmail("admin@pointage.tn");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setRole(User.Role.ADMIN);
            admin.setTeam(User.Team.B2B);
            userRepository.save(admin);
        }

        List<UserData> users = List.of(
                new UserData("Afef Ayari", "afef.ayari@pointage.tn", User.Team.B2B),
                new UserData("Lobna Hammami", "lobna.hammami@pointage.tn", User.Team.B2B),
                new UserData("Nabil Dhouir", "nabil.dhouir@pointage.tn", User.Team.B2B),
                new UserData("Med Ali Essifi", "medali.essifi@pointage.tn", User.Team.B2B),
                new UserData("Mohamed Ferjene", "mohamed.ferjene@pointage.tn", User.Team.B2B),
                new UserData("Fatma Mejri", "fatma.mejri@pointage.tn", User.Team.B2B),
                new UserData("Aamer Hammami", "aamer.hammami@pointage.tn", User.Team.GP),
                new UserData("Mehdi Cheffi", "mehdi.cheffi@pointage.tn", User.Team.GP),
                new UserData("Yosr Gharbi", "yosr.gharbi@pointage.tn", User.Team.GP),
                new UserData("Mohamed Ben Marzouk", "mohamed.benmarzouk@pointage.tn", User.Team.GP)
        );

        for (UserData ud : users) {
            if (userRepository.findByEmail(ud.email).isEmpty()) {
                User user = new User();
                user.setFullName(ud.fullName);
                user.setEmail(ud.email);
                user.setPassword(passwordEncoder.encode("password123"));
                user.setRole(User.Role.USER);
                user.setTeam(ud.team);
                userRepository.save(user);
            }
        }
    }

    private record UserData(String fullName, String email, User.Team team) {
    }
}