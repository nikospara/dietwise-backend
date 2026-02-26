package eu.dietwise.services.nondomain;

import java.time.LocalDateTime;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
class DateTimeServiceImpl implements DateTimeService {
	@Override
	public LocalDateTime getNow() {
		return LocalDateTime.now();
	}

	@Override
	public long currentTimeMillis() {
		return System.currentTimeMillis();
	}
}
