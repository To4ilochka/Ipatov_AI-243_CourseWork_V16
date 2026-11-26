package ua.op.edu.ipatov.volunteer.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import ua.op.edu.ipatov.volunteer.repository.UserAccountRepository;

@Service
public class PlatformUserDetailsService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;

    public PlatformUserDetailsService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userAccountRepository.findByEmailIgnoreCase(username)
                .map(PlatformUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Некоректна електронна пошта або пароль"));
    }
}
