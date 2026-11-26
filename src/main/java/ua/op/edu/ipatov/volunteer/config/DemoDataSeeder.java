package ua.op.edu.ipatov.volunteer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ua.op.edu.ipatov.volunteer.domain.*;
import ua.op.edu.ipatov.volunteer.repository.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ідемпотентне наповнення бази синтетичним навчальним набором даних (нормальні записи,
 * заповнені ліміти, завершені завдання, конфліктні переходи та достатній обсяг для пагінації).
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    public static final String DEMO_PASSWORD = "VoluntHub#2026!SafePassword";

    private final SkillRepository skillRepository;
    private final UserAccountRepository userRepository;
    private final InitiativeRepository initiativeRepository;
    private final VolunteerTaskRepository taskRepository;
    private final TaskApplicationRepository applicationRepository;
    private final ExecutionReportRepository reportRepository;
    private final AuditEventRepository auditRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(SkillRepository skillRepository,
                          UserAccountRepository userRepository,
                          InitiativeRepository initiativeRepository,
                          VolunteerTaskRepository taskRepository,
                          TaskApplicationRepository applicationRepository,
                          ExecutionReportRepository reportRepository,
                          AuditEventRepository auditRepository,
                          PasswordEncoder passwordEncoder) {
        this.skillRepository = skillRepository;
        this.userRepository = userRepository;
        this.initiativeRepository = initiativeRepository;
        this.taskRepository = taskRepository;
        this.applicationRepository = applicationRepository;
        this.reportRepository = reportRepository;
        this.auditRepository = auditRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (skillRepository.count() > 0) {
            log.info("Демонстраційний набір даних уже присутній у СКБД, повторне наповнення пропущено.");
            return;
        }
        log.info("Початкове ідемпотентне завантаження синтетичних навчальних даних VoluntHub...");

        List<Skill> savedSkills = skillRepository.saveAll(List.of(
                new Skill("FIRST_AID", "Домедична просвіта та інструктаж", "Безпека і здоров'я"),
                new Skill("ECO_SORT", "Сортування вторсировини та екоаудит", "Екологія"),
                new Skill("PARK_CARE", "Озеленення та догляд за деревами", "Екологія"),
                new Skill("LOGISTICS", "Складський облік і маршрутна логістика", "Організація"),
                new Skill("EVENT_HOST", "Модерація освітніх подій", "Організація"),
                new Skill("TUTORING_STEM", "Менторство з математики та інформатики", "Освіта"),
                new Skill("DIGITAL_LIT", "Цифрова грамотність для літніх людей", "Освіта"),
                new Skill("WEB_ACCESS", "Вебдоступність (WCAG) та верстка", "ІТ-підтримка"),
                new Skill("DATA_CLEAN", "Впорядкування відкритих таблиць і звітів", "ІТ-підтримка"),
                new Skill("TRANSLATION_EN", "Письмовий переклад англійською", "Комунікації"),
                new Skill("PHOTO_DOC", "Фотофіксація публічних заходів", "Комунікації"),
                new Skill("ANIMAL_CARE", "Догляд і соціалізація тварин у притулку", "Зоозахист")
        ));
        Map<String, Skill> s = savedSkills.stream().collect(Collectors.toMap(Skill::getCode, Function.identity()));

        String encodedPass = passwordEncoder.encode(DEMO_PASSWORD);

        UserAccount coordOdesa = userRepository.save(new UserAccount(
                "coord.odesa@volunthub.op.edu.ua", encodedPass,
                "Коваленко Оксана Ігорівна", UserRole.COORDINATOR,
                "Одеса", "+380501112233", "@kovalenko_coord",
                "Координаторка міських освітніх та екологічних волонтерських програм в Одесі."));

        UserAccount coordLviv = userRepository.save(new UserAccount(
                "coord.lviv@volunthub.op.edu.ua", encodedPass,
                "Савчук Тарас Романович", UserRole.COORDINATOR,
                "Львів", "+380672223344", "@savchuk_lviv",
                "Координатор цифрових освітніх ініціатив і центрів підтримки тварин."));

        UserAccount volOlena = new UserAccount(
                "vol.ivanenko@volunthub.op.edu.ua", encodedPass,
                "Іваненко Олена Сергіївна", UserRole.VOLUNTEER,
                "Одеса", "+380931234567", "@olena_vol",
                "Студентка комп'ютерних наук, викладаю цифрову грамотність та допомагаю з вебдоступністю.");
        volOlena.getSkills().add(new VolunteerSkill(volOlena, s.get("DIGITAL_LIT"), (short) 3, "2 роки воркшопів у бібліотеці"));
        volOlena.getSkills().add(new VolunteerSkill(volOlena, s.get("WEB_ACCESS"), (short) 3, "Аудит WCAG 2.2 AA"));
        volOlena.getSkills().add(new VolunteerSkill(volOlena, s.get("TUTORING_STEM"), (short) 2, "Гуртки інформатики"));
        volOlena.getSkills().add(new VolunteerSkill(volOlena, s.get("ECO_SORT"), (short) 2, "Участь в екотолоках"));
        volOlena = userRepository.save(volOlena);

        UserAccount volDmytro = new UserAccount(
                "vol.koval@volunthub.op.edu.ua", encodedPass,
                "Коваль Дмитро Олександрович", UserRole.VOLUNTEER,
                "Одеса", "+380632345678", "@dmytro_koval",
                "Маю досвід складської логістики, озеленення парків і проведення екоакцій.");
        volDmytro.getSkills().add(new VolunteerSkill(volDmytro, s.get("LOGISTICS"), (short) 3, "Координація доставок інвентарю"));
        volDmytro.getSkills().add(new VolunteerSkill(volDmytro, s.get("PARK_CARE"), (short) 3, "Висадка саджанців"));
        volDmytro.getSkills().add(new VolunteerSkill(volDmytro, s.get("FIRST_AID"), (short) 2, "Сертифікований тренінг"));
        volDmytro = userRepository.save(volDmytro);

        UserAccount volMarta = new UserAccount(
                "vol.melnyk@volunthub.op.edu.ua", encodedPass,
                "Мельник Марта Андріївна", UserRole.VOLUNTEER,
                "Львів", "+380973456789", "@marta_melnyk",
                "Перекладачка англійської, волонтерка притулку для тварин та фотографка подій.");
        volMarta.getSkills().add(new VolunteerSkill(volMarta, s.get("TRANSLATION_EN"), (short) 3, "Переклад освітніх посібників"));
        volMarta.getSkills().add(new VolunteerSkill(volMarta, s.get("ANIMAL_CARE"), (short) 3, "Щосуботні зміни у притулку"));
        volMarta.getSkills().add(new VolunteerSkill(volMarta, s.get("PHOTO_DOC"), (short) 2, "Репортажна зйомка"));
        volMarta = userRepository.save(volMarta);

        UserAccount volNewbie = new UserAccount(
                "vol.newbie@volunthub.op.edu.ua", encodedPass,
                "Бондар Артем Віталійович", UserRole.VOLUNTEER,
                "Київ", "+380664567890", "@artem_bondar",
                "Новий учасник платформи без поданих заявок (для демонстрації порожнього стану кабінету).");
        volNewbie.getSkills().add(new VolunteerSkill(volNewbie, s.get("DATA_CLEAN"), (short) 2, "Обробка таблиць CSV"));
        userRepository.save(volNewbie);

        LocalDate today = LocalDate.now();
        Instant now = Instant.now();

        Initiative initEco = initiativeRepository.save(new Initiative(
                coordOdesa,
                "Зелений берег та екопросвіта громад",
                "Екологія",
                "Одеса",
                "Навчальна екологічна програма впорядкування паркових зон, сортування вторсировини та інформування містян.",
                InitiativeStatus.ACTIVE,
                today.minusDays(10),
                today.plusDays(60)));

        Initiative initDigital = initiativeRepository.save(new Initiative(
                coordOdesa,
                "Цифрова доступність та відкрита школа",
                "Освіта та ІТ",
                "Одеса",
                "Серія безоплатних занять із цифрової грамотності, STEM-менторства для школярів та перевірки доступності освітніх сайтів.",
                InitiativeStatus.ACTIVE,
                today.minusDays(5),
                today.plusDays(90)));

        Initiative initShelter = initiativeRepository.save(new Initiative(
                coordLviv,
                "Турбота про муніципальні притулки для тварин",
                "Зоозахист",
                "Львів",
                "Організація вигулу, соціалізації тварин, обліку кормів та інформаційних карток для майбутніх господарів.",
                InitiativeStatus.ACTIVE,
                today.minusDays(14),
                today.plusDays(75)));

        Initiative initOpenKnowledge = initiativeRepository.save(new Initiative(
                coordLviv,
                "Переклад відкритих навчальних курсів українською",
                "Освіта та ІТ",
                "Львів",
                "Командна адаптація відкритих інженерних конспектів, вичитка термінології та верстка доступних конспектів.",
                InitiativeStatus.ACTIVE,
                today.minusDays(7),
                today.plusDays(120)));

        Initiative initArchive = initiativeRepository.save(new Initiative(
                coordOdesa,
                "Літній лекторій безпеки на воді (завершений сезон)",
                "Безпека і здоров'я",
                "Одеса",
                "Завершена сезонна ініціатива з просвітницьких інструктажів у міських бібліотеках і молодіжних просторах.",
                InitiativeStatus.COMPLETED,
                today.minusDays(120),
                today.minusDays(30)));

        Initiative initDraft = initiativeRepository.save(new Initiative(
                coordLviv,
                "Зимовий марафон відкритих наукових гуртків (чернетка)",
                "Освіта та ІТ",
                "Львів",
                "Внутрішня чернетка майбутньої освітньої ініціативи, яка ще не опублікована у відкритому каталозі.",
                InitiativeStatus.DRAFT,
                today.plusDays(15),
                today.plusDays(85)));

        // 15 змістовних завдань (для пагінації по 6 карток на сторінку = 3 сторінки + граничні стани)
        VolunteerTask t1 = createSeedTask(initDigital,
                "Аудит клавіатурної навігації та контрасту навчального порталу",
                "Перевірити 12 сторінок відкритого студентського довідника за критеріями WCAG 2.2 AA та підготувати перелік правок.",
                "Дистанційно (відеозустріч у навчальному чаті о 17:00)",
                WorkFormat.REMOTE, TaskUrgency.CRITICAL, 3, "8.0", now.plus(14, ChronoUnit.DAYS),
                List.of(req(s.get("WEB_ACCESS"), 2, true, 5), req(s.get("DATA_CLEAN"), 1, false, 2)));

        VolunteerTask t2 = createSeedTask(initDigital,
                "Проведення практикуму з безпечної роботи зі смартфоном для літніх відвідувачів",
                "Двогодинне практичне заняття у читальній залі №2: налаштування масштабу шрифту, захист від фішингу, електронні черги.",
                "м. Одеса, Міська бібліотека ім. І. Франка, зала №2 (навчальна локація)",
                WorkFormat.ONSITE, TaskUrgency.HIGH, 4, "4.5", now.plus(10, ChronoUnit.DAYS),
                List.of(req(s.get("DIGITAL_LIT"), 2, true, 5), req(s.get("EVENT_HOST"), 1, false, 2)));

        VolunteerTask t3 = createSeedTask(initDigital,
                "Менторські онлайн-консультації з алгоритмів для учнів 9–10 класів",
                "Провести три групові розбори задач на масиви, графи та сортування у вечірній час.",
                "Дистанційно через освітню платформу",
                WorkFormat.REMOTE, TaskUrgency.NORMAL, 5, "6.0", now.plus(25, ChronoUnit.DAYS),
                List.of(req(s.get("TUTORING_STEM"), 2, true, 4)));

        VolunteerTask t4 = createSeedTask(initEco,
                "Організація станції роздільного збору пластику й паперу в університетському сквері",
                "Маркування контейнерів, зважування зібраних фракцій та коротка консультація учасників акції.",
                "м. Одеса, Студентський сквер біля навчального корпусу (навчальна локація)",
                WorkFormat.ONSITE, TaskUrgency.HIGH, 6, "5.0", now.plus(8, ChronoUnit.DAYS),
                List.of(req(s.get("ECO_SORT"), 2, true, 4), req(s.get("LOGISTICS"), 1, false, 3)));

        VolunteerTask t5 = createSeedTask(initEco,
                "Висадка та мульчування молодих кленів уздовж пішохідної алеї",
                "Підготовка лунок, підвезення мульчі та фіксація кількості висаджених дерев у відомості.",
                "м. Одеса, Паркова алея, сектор B (навчальна локація)",
                WorkFormat.ONSITE, TaskUrgency.NORMAL, 8, "6.5", now.plus(18, ChronoUnit.DAYS),
                List.of(req(s.get("PARK_CARE"), 1, false, 4), req(s.get("FIRST_AID"), 1, false, 2)));

        // Граничний стан 1: ліміт учасників повністю заповнено (1 із 1)
        VolunteerTask t6Full = createSeedTask(initEco,
                "Координація інвентарю та видачі рукавичок для суботньої толоки",
                "Облік виданого садового інвентарю перед початком толоки та перевірка повернення на склад.",
                "м. Одеса, Господарський павільйон скверу (навчальна локація)",
                WorkFormat.ONSITE, TaskUrgency.CRITICAL, 1, "4.0", now.plus(5, ChronoUnit.DAYS),
                List.of(req(s.get("LOGISTICS"), 2, true, 5)));

        VolunteerTask t7 = createSeedTask(initShelter,
                "Ранкова соціалізація та вигул собак у тренувальному секторі",
                "Прогулянка за затвердженим графіком, оновлення спостережень щодо поведінки тварин для кінолога.",
                "м. Львів, Навчально-адаптаційний майданчик притулку, сектор 3",
                WorkFormat.ONSITE, TaskUrgency.HIGH, 4, "4.0", now.plus(9, ChronoUnit.DAYS),
                List.of(req(s.get("ANIMAL_CARE"), 2, true, 5)));

        VolunteerTask t8 = createSeedTask(initShelter,
                "Фотозйомка тварин і підготовка двомовних анкет для каталогу адопції",
                "Зробити чіткі портретні фото при денному освітленні та перекласти короткі описи англійською мовою.",
                "м. Львів / гібридно (зйомка на майданчику, обробка анкет вдома)",
                WorkFormat.HYBRID, TaskUrgency.NORMAL, 3, "5.5", now.plus(20, ChronoUnit.DAYS),
                List.of(req(s.get("PHOTO_DOC"), 2, true, 4), req(s.get("TRANSLATION_EN"), 2, false, 3)));

        VolunteerTask t9 = createSeedTask(initShelter,
                "Впорядкування електронної відомості залишків кормів за місяць",
                "Звірити накладні надходження із фактичними картками витрат у форматі електронних таблиць.",
                "Дистанційно (обмін таблицями з координатором)",
                WorkFormat.REMOTE, TaskUrgency.NORMAL, 2, "3.5", now.plus(16, ChronoUnit.DAYS),
                List.of(req(s.get("DATA_CLEAN"), 2, true, 4), req(s.get("LOGISTICS"), 1, false, 2)));

        VolunteerTask t10 = createSeedTask(initOpenKnowledge,
                "Переклад розділу про структури даних та оцінку складності алгоритмів",
                "Перекласти 18 сторінок відкритого конспекту українською мовою з уніфікацією термінів за словником проєкту.",
                "Дистанційно (Git-репозиторій перекладів)",
                WorkFormat.REMOTE, TaskUrgency.HIGH, 4, "10.0", now.plus(21, ChronoUnit.DAYS),
                List.of(req(s.get("TRANSLATION_EN"), 2, true, 5), req(s.get("TUTORING_STEM"), 1, false, 3)));

        VolunteerTask t11 = createSeedTask(initOpenKnowledge,
                "Семантична HTML-розмітка формул і таблиць для екранних читачів",
                "Адаптувати готові матеріали курсу для коректного озвучення скрінрідерами та перевірити порядок заголовків.",
                "Дистанційно",
                WorkFormat.REMOTE, TaskUrgency.HIGH, 3, "7.0", now.plus(19, ChronoUnit.DAYS),
                List.of(req(s.get("WEB_ACCESS"), 2, true, 5)));

        VolunteerTask t12 = createSeedTask(initOpenKnowledge,
                "Модерація онлайн-зустрічі перекладачів і фіксація термінологічних рішень",
                "Провести щотижневу синхронізацію на 60 хвилин та оновити глосарій узгоджених термінів.",
                "Дистанційно",
                WorkFormat.REMOTE, TaskUrgency.NORMAL, 2, "2.5", now.plus(12, ChronoUnit.DAYS),
                List.of(req(s.get("EVENT_HOST"), 2, true, 4), req(s.get("TRANSLATION_EN"), 1, false, 2)));

        VolunteerTask t13 = createSeedTask(initEco,
                "Підготовка інфографіки та текстових пам'яток із правил компостування листя",
                "Упорядкувати перевірені рекомендації екологів у зручну текстову пам'ятку з альтернативними описами зображень.",
                "Дистанційно",
                WorkFormat.REMOTE, TaskUrgency.NORMAL, 3, "4.0", now.plus(28, ChronoUnit.DAYS),
                List.of(req(s.get("ECO_SORT"), 1, false, 3), req(s.get("WEB_ACCESS"), 1, false, 2)));

        // Граничний стан 2: завершене завдання у завершеній ініціативі (негативний тест подання заявки)
        VolunteerTask t14Completed = createSeedTask(initArchive,
                "Проведення відкритого інструктажу з домедичної допомоги у молодіжному центрі",
                "Демонстрація алгоритму виклику екстрених служб і базових дій до приїзду медиків.",
                "м. Одеса, Молодіжний освітній простір, зала А (навчальна локація)",
                WorkFormat.ONSITE, TaskUrgency.NORMAL, 2, "3.0", now.minus(35, ChronoUnit.DAYS),
                List.of(req(s.get("FIRST_AID"), 2, true, 5)));
        t14Completed.setStatus(TaskStatus.COMPLETED);
        taskRepository.save(t14Completed);

        // Граничний стан 3: завдання у чернетці ініціативи (не видиме гостям у публічному каталозі)
        createSeedTask(initDraft,
                "Тестування програми зимового гуртка з робототехніки",
                "Попереднє внутрішнє узгодження списку навчальних наборів перед публікацією анонсу.",
                "м. Львів, Навчальна лабораторія №4",
                WorkFormat.ONSITE, TaskUrgency.NORMAL, 2, "5.0", now.plus(30, ChronoUnit.DAYS),
                List.of(req(s.get("TUTORING_STEM"), 2, true, 4)));

        // Демонстраційні заявки і звіти різних користувачів
        t6Full.incrementApprovedCount(); // заповнює 1/1 і переводить у IN_PROGRESS
        taskRepository.save(t6Full);
        TaskApplication appDmytroFull = new TaskApplication(t6Full, volDmytro,
                "Маю досвід складського обліку на попередніх чотирьох екоакціях, готовий прибути за годину до старту.",
                new BigDecimal("96.00"));
        appDmytroFull.approve(coordOdesa, "Призначено відповідальним за видачу інвентарю.", now.minus(1, ChronoUnit.DAYS));
        appDmytroFull = applicationRepository.save(appDmytroFull);

        t2.incrementApprovedCount();
        taskRepository.save(t2);
        TaskApplication appOlenaApproved = new TaskApplication(t2, volOlena,
                "Проводила аналогічні практикуми з налаштування смартфонів у цій бібліотеці минулого місяця.",
                new BigDecimal("94.00"));
        appOlenaApproved.approve(coordOdesa, "Погоджено, чекаємо у залі №2 о 10:30.", now.minus(2, ChronoUnit.DAYS));
        appOlenaApproved = applicationRepository.save(appOlenaApproved);

        TaskApplication appOlenaPending = applicationRepository.save(new TaskApplication(
                t1, volOlena,
                "Маю практичний досвід аудиту WCAG 2.2 AA та перевірки клавіатурного фокусу, виконаю за вихідні.",
                new BigDecimal("98.50")));

        t7.incrementApprovedCount();
        taskRepository.save(t7);
        TaskApplication appMartaApproved = new TaskApplication(t7, volMarta,
                "Працюю з цим сектором щосуботи, добре знаю правила вигулу та фіксації спостережень.",
                new BigDecimal("97.00"));
        appMartaApproved.approve(coordLviv, "Підтверджено на суботню зміну.", now.minus(3, ChronoUnit.DAYS));
        appMartaApproved = applicationRepository.save(appMartaApproved);

        TaskApplication appMartaPending = applicationRepository.save(new TaskApplication(
                t10, volMarta,
                "Готова взяти переклад розділу про структури даних, маю досвід технічного перекладу.",
                new BigDecimal("92.50")));

        // Підсумкові звіти (один на перевірці, один уже підтверджений)
        ExecutionReport repOlena = reportRepository.save(new ExecutionReport(
                appOlenaApproved, volOlena,
                "Проведено двогодинне заняття для 14 відвідувачів бібліотеки: налаштовано масштаб тексту, перевірено двофакторний захист пошти та роздано друковані пам'ятки.",
                new BigDecimal("4.5"),
                "Охоплено 14 учасників, роздано 14 друкованих пам'яток."));

        ExecutionReport repMarta = new ExecutionReport(
                appMartaApproved, volMarta,
                "Виконано ранкову прогулянку та базову соціалізацію для 6 собак сектору №3, оновлено картки спостережень для кінолога.",
                new BigDecimal("4.0"),
                "6 тварин вигуляно, оновлено 6 карток поведінки.");
        repMarta.verify("Дякуємо, картки перевірені кінологом без зауважень.", now.minus(1, ChronoUnit.DAYS));
        repMarta = reportRepository.save(repMarta);

        auditRepository.saveAll(List.of(
                new AuditEvent("SYSTEM", 0L, null, "SEED_INITIALIZED",
                        "Створено 12 навичок, 6 користувачів, 6 ініціатив, 15 завдань, 5 заявок та 2 звіти"),
                new AuditEvent("APPLICATION", appOlenaPending.getId(), volOlena.getId(), "APPLICATION_SUBMITTED",
                        "Заявка очікує рішення координатора"),
                new AuditEvent("APPLICATION", appMartaPending.getId(), volMarta.getId(), "APPLICATION_SUBMITTED",
                        "Заявка на переклад очікує розгляду"),
                new AuditEvent("REPORT", repOlena.getId(), volOlena.getId(), "REPORT_SUBMITTED",
                        "Звіт очікує підтвердження координатором"),
                new AuditEvent("REPORT", repMarta.getId(), coordLviv.getId(), "REPORT_VERIFIED",
                        "Звіт підтверджено координатором")
        ));
        log.info("Демонстраційний набір даних VoluntHub успішно завантажено.");
    }

    private record SkillReqSeed(Skill skill, int minLevel, boolean mandatory, int weight) {
    }

    private static SkillReqSeed req(Skill skill, int minLevel, boolean mandatory, int weight) {
        return new SkillReqSeed(skill, minLevel, mandatory, weight);
    }

    private VolunteerTask createSeedTask(Initiative initiative,
                                         String title,
                                         String description,
                                         String locationText,
                                         WorkFormat format,
                                         TaskUrgency urgency,
                                         int maxVolunteers,
                                         String hours,
                                         Instant deadline,
                                         List<SkillReqSeed> reqs) {
        VolunteerTask task = new VolunteerTask(
                initiative, title, description, locationText, format, urgency,
                maxVolunteers, new BigDecimal(hours), deadline);
        for (SkillReqSeed r : reqs) {
            task.getRequiredSkills().add(new TaskRequiredSkill(
                    task, r.skill(), (short) r.minLevel(), r.mandatory(), (short) r.weight()));
        }
        return taskRepository.save(task);
    }
}
