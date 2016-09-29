package com.capitalone.dashboard.model;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class GitLabRepoTests {

	private GitLabRepo gitlabRepo1;
	private GitLabRepo gitlabRepo2;
	private GitLabRepo gitlabRepo3;	
	
	@Before
    public void init() {
		gitlabRepo1 = new GitLabRepo();
		gitlabRepo1.setRepoUrl("https://github.com/capitalone/Hygiea.git");
		gitlabRepo1.setBranch("master");
		gitlabRepo2 = new GitLabRepo();
		gitlabRepo2.setRepoUrl("https://github.com/capitalone/Hygiea.git");
        gitlabRepo2.setBranch("master");
        gitlabRepo3=new GitLabRepo();
        gitlabRepo3.setRepoUrl("https://github.com/capitalone/Hygieas.git");
        gitlabRepo3.setBranch("master");
    }
	
	@Test
	public void testEquals() throws Exception {
		boolean x=gitlabRepo1.equals(gitlabRepo2);
		assertTrue(x);
	}
	
	@Test
	public void testHashCode() throws Exception {
		int hashcode1=gitlabRepo1.hashCode();
		int hashcode2=gitlabRepo2.hashCode();
		
		assertEquals(hashcode1, hashcode2);
	}
	
	@Test
	public void testEqualsNegative() throws Exception {
			boolean y=gitlabRepo3.equals(gitlabRepo1);
			assertTrue(!y);
		}
	
	@Test
	public void testHashCodeNegative() throws Exception {
		int hashcode1=gitlabRepo1.hashCode();
		int hashcode3=gitlabRepo3.hashCode();
		assertTrue(hashcode1!=hashcode3);
	}
}
