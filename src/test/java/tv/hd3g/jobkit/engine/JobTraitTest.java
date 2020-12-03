package tv.hd3g.jobkit.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Random;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

class JobTraitTest {

	static Random random = new Random();

	@Mock
	Runnable task;
	@Mock
	Consumer<Exception> afterRunCommand;
	@Mock
	Job job;

	String name;
	String spoolName;
	int priority;

	class Trait implements JobTrait {

		int triggerCount = 0;
		String name;
		String spoolName;
		int priority;
		Runnable task;
		Consumer<Exception> afterRunCommand;

		@Override
		public boolean runOneShot(final String name,
		                          final String spoolName,
		                          final int priority,
		                          final Runnable task,
		                          final Consumer<Exception> afterRunCommand) {
			triggerCount++;
			this.name = name;
			this.spoolName = spoolName;
			this.priority = priority;
			this.task = task;
			this.afterRunCommand = afterRunCommand;
			return true;
		}

	}

	Trait trait;

	@BeforeEach
	void init() throws Exception {
		MockitoAnnotations.openMocks(this).close();
		name = String.valueOf(random.nextLong());
		spoolName = String.valueOf(random.nextLong());
		priority = random.nextInt();

		when(job.getJobName()).thenReturn(name);
		when(job.getJobSpoolname()).thenReturn(spoolName);
		when(job.getJobPriority()).thenReturn(priority);

		trait = new Trait();
	}

	@Test
	void testRunOneShotJob() {
		trait.runOneShot(job);

		assertEquals(1, trait.triggerCount);
		assertEquals(name, trait.name);
		assertEquals(spoolName, trait.spoolName);
		assertEquals(priority, trait.priority);
		assertNotNull(trait.task);
		assertNotNull(trait.afterRunCommand);

		verify(job, never()).onJobStart();
		verify(job, never()).run();
		verify(job, never()).onJobDone();
		verify(job, never()).onJobFail(any(Exception.class));

		trait.task.run();
		verify(job, times(1)).onJobStart();
		verify(job, times(1)).run();
		verify(job, never()).onJobDone();
		verify(job, never()).onJobFail(any(Exception.class));

		trait.afterRunCommand.accept(null);
		verify(job, times(1)).onJobStart();
		verify(job, times(1)).run();
		verify(job, times(1)).onJobDone();
		verify(job, never()).onJobFail(any(Exception.class));

		final var exception = Mockito.mock(Exception.class);
		trait.afterRunCommand.accept(exception);
		verify(job, times(1)).onJobStart();
		verify(job, times(1)).run();
		verify(job, times(1)).onJobDone();
		verify(job, times(1)).onJobFail(any(Exception.class));
		verify(job, times(1)).onJobFail(eq(exception));
	}

}
