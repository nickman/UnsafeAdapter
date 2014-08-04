template = """
	/**
	 * Tests an auto de-allocated long memory allocation 
	 * @throws Exception thrown on any error
	 */
	
	@Test	
	public void testAutoClearedAllocatedLong() throws Exception {
		DefaultAssignableDeAllocateMe dealloc = new DefaultAssignableDeAllocateMe(1); 
		final long address = UnsafeAdapter.allocateMemory(8, dealloc);
		long value = nextPosLong();
		UnsafeAdapter.putLong(address, value);
		Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.getLong(address));
		Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.getLong(address));
		validateAllocated(8, -1);
		log("MemoryBean State After Alloc: %s", UnsafeAdapter.getMemoryMBean().getState());
		dealloc = null;
		System.gc();
		sleep(100);
		log("MemoryBean State After Clear: %s", UnsafeAdapter.getMemoryMBean().getState());
		validateDeallocated(0, -1);		
	}

""";

pop = { type, size ->
    def clazz = Class.forName("java.lang.$type");
    def prim = clazz.TYPE.getName();
    return template.replace("##Type##", type).replace("##type##", prim).replace("##size##", "$size");
}

println pop("Integer", 4);

return null;