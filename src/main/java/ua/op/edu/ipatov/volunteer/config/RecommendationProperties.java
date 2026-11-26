package ua.op.edu.ipatov.volunteer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфігураційні вагові коефіцієнти алгоритму рекомендацій завдань за навичками.
 */
@ConfigurationProperties(prefix = "app.recommendation")
public record RecommendationProperties(
        double alphaSkill,
        double betaLocation,
        double gammaUrgency,
        double missingMandatoryPenalty
) {
    public RecommendationProperties {
        if (alphaSkill <= 0 && betaLocation <= 0 && gammaUrgency <= 0) {
            alphaSkill = 0.70;
            betaLocation = 0.20;
            gammaUrgency = 0.10;
        }
        if (missingMandatoryPenalty <= 0 || missingMandatoryPenalty > 1.0) {
            missingMandatoryPenalty = 0.35;
        }
    }
}
