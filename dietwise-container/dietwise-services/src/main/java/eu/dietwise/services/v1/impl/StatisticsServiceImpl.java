package eu.dietwise.services.v1.impl;

import static eu.dietwise.common.utils.UniComprehensions.forc;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Function;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.dao.statistics.UserStatsEntityDao;
import eu.dietwise.services.nondomain.DateTimeService;
import eu.dietwise.services.v1.StatisticsService;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class StatisticsServiceImpl implements StatisticsService {
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final UserStatsEntityDao userStatsEntityDao;
	private final DateTimeService dateTimeService;

	public StatisticsServiceImpl(ReactivePersistenceContextFactory persistenceContextFactory, UserStatsEntityDao userStatsEntityDao, DateTimeService dateTimeService) {
		this.persistenceContextFactory = persistenceContextFactory;
		this.userStatsEntityDao = userStatsEntityDao;
		this.dateTimeService = dateTimeService;
	}

	@Override
	public Uni<User> markUserActivity(User user) {
		if (user == null || user.getApplicationId().isEmpty()) return Uni.createFrom().item(user);
		LocalDateTime now = dateTimeService.getNow();
		String applicationId = user.getApplicationId().get();
		UUID userId = UUID.fromString(user.getId().asString());
		return persistenceContextFactory.withTransaction(tx -> forc(
				userStatsEntityDao.setLastSeen(tx, applicationId, userId, now),
				increaseDaysLaunchedIfNeeded(tx, user, user.getApplicationId().get(), userId, now)
		));
	}

	private Function<? super LocalDateTime, Uni<? extends User>> increaseDaysLaunchedIfNeeded(ReactivePersistenceTxContext tx, User user, String applicationId, UUID userId, LocalDateTime now) {
		return lastSeen -> {
			if (lastSeen == null || lastSeen.isBefore(now.minusDays(1))) {
				return userStatsEntityDao.increaseDaysLaunched(tx, applicationId, userId).replaceWith(user);
			} else {
				return Uni.createFrom().item(user);
			}
		};
	}
}
