package com.capitalone.dashboard.collector;

import com.capitalone.dashboard.model.TestCase;
import com.capitalone.dashboard.model.TestCaseStatus;
import com.capitalone.dashboard.model.TestSuite;
import com.capitalone.dashboard.model.TestSuiteType;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import org.apache.commons.io.IOUtils;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author wimcki
 */
public class BehaveJsonToTestResultTransformerTest {
    BehaveJsonToTestResultTransformer transformer = new BehaveJsonToTestResultTransformer();
    
    @Test
    public void testTransform() throws Exception {
        
        String json = getJson("two-features.json");

        Iterable<TestSuite> suites = transformer.transform(json);
        assertThat(suites, notNullValue());
        
        Iterator<TestSuite> suiteIt = suites.iterator();
        Iterator<TestCase> testCaseIt;
        TestSuite suite;

        suite = suiteIt.next();
        testCaseIt = suite.getTestCases().iterator();
        assertSuite(suite, "Feature:Check that SSH port is reachable on remote servers", 2, 0, 0, 2, 3882588L);
        
        assertTestCase(testCaseIt.next(), "All servers are reachable", "Scenario:All servers are reachable", 1640862L, TestCaseStatus.Success);
        assertThat(testCaseIt.hasNext(), is(true));
    }
    
    private void assertSuite(TestSuite suite, String desc, int success, int fail, int skip, int total, long duration) {
        
        assertThat(suite.getType(), is(TestSuiteType.Functional));
        assertThat(suite.getDescription(), is(desc));
        assertThat(suite.getFailedTestCaseCount(), is(fail));
        assertThat(suite.getSuccessTestCaseCount(), is(success));
        assertThat(suite.getSkippedTestCaseCount(), is(skip));
        assertThat(suite.getTotalTestCaseCount(), is(total));
        assertThat(suite.getDuration(), is(duration));
        assertThat(suite.getStartTime(), is(0l));
        assertThat(suite.getEndTime(), is(0l));
    }
    
    private void assertTestCase(TestCase tc, String id, String name, long duration, TestCaseStatus status) {
        assertThat(tc.getId(), is(id));
        assertThat(tc.getDescription(), is(name));
        assertThat(tc.getDuration(), is(duration));
        assertThat(tc.getStatus(), is(status));
    }

    private String getJson(String fileName) throws IOException {
        InputStream inputStream = BehaveJsonToTestResultTransformerTest.class.getResourceAsStream(fileName);
        return IOUtils.toString(inputStream);
    }
    
}
