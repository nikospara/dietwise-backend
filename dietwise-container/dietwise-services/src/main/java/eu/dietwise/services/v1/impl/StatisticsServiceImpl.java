package eu.dietwise.services.v1.impl;

import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
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
		return null;
	}
}
