package ua.op.edu.ipatov.volunteer.service;

import org.springframework.stereotype.Component;
import ua.op.edu.ipatov.volunteer.domain.TaskApplication;
import ua.op.edu.ipatov.volunteer.domain.UserAccount;
import ua.op.edu.ipatov.volunteer.domain.UserRole;
import ua.op.edu.ipatov.volunteer.dto.ApiDtos.ParticipantContactDto;
import ua.op.edu.ipatov.volunteer.exception.AccessPolicyViolationException;

/**
 * Довірена серверна політика розкриття контактних даних учасників (Варіант 16).
 * Контакти волонтера відкриваються виключно самому власнику профілю або координатору
 * конкретної ініціативи, до якої волонтер подав активну чи погоджену заявку.
 */
@Component
public class ContactVisibilityPolicy {

    public boolean canViewVolunteerContacts(UserAccount viewer, TaskApplication application) {
        if (viewer == null || application == null) {
            return false;
        }
        Long volunteerId = application.getVolunteer().getId();
        if (viewer.getId() != null && viewer.getId().equals(volunteerId)) {
            return true;
        }
        if (viewer.getRole() == UserRole.COORDINATOR) {
            Long ownerCoordinatorId = application.getTask().getInitiative().getCoordinator().getId();
            return ownerCoordinatorId != null
                    && ownerCoordinatorId.equals(viewer.getId())
                    && application.getStatus().isActive();
        }
        return false;
    }

    public ParticipantContactDto buildContactDto(UserAccount viewer, TaskApplication application) {
        UserAccount volunteer = application.getVolunteer();
        if (canViewVolunteerContacts(viewer, application)) {
            return new ParticipantContactDto(
                    volunteer.getId(),
                    volunteer.getFullName(),
                    volunteer.getCity(),
                    true,
                    volunteer.getEmail(),
                    volunteer.getPhone(),
                    volunteer.getTelegramHandle()
            );
        }
        return new ParticipantContactDto(
                volunteer.getId(),
                volunteer.getFullName(),
                volunteer.getCity(),
                false,
                null,
                null,
                null
        );
    }

    public ParticipantContactDto requireAuthorizedContacts(UserAccount viewer, TaskApplication application) {
        if (!canViewVolunteerContacts(viewer, application)) {
            throw new AccessPolicyViolationException(
                    "Доступ до контактних даних учасника дозволений лише відповідальному координатору цієї ініціативи або самому волонтеру");
        }
        return buildContactDto(viewer, application);
    }
}
