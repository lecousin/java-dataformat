package net.lecousin.dataformat.model;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import net.lecousin.framework.collections.LinkedArrayList;
import net.lecousin.framework.event.Event;
import net.lecousin.framework.event.SimpleEvent;

public class ModelBlock extends AbstractModelElement implements IModelLocation {

	public ModelBlock(ModelBlock parent, String name, long offset, long length) {
		super(parent, name);
		this.offset = offset;
		this.length = length;
	}
	
	protected long offset;
	protected long length;
	protected List<ModelVariable<?>> vars = new LinkedList<>();
	protected List<ModelBlock> blocks = new LinkedList<>();

	public Event<ModelVariable<?>> variableAdded = new Event<>();
	public Event<ModelVariable<?>> variableRemoved = new Event<>();
	public Event<ModelBlock> blockAdded = new Event<>();
	public Event<ModelBlock> blockRemoved = new Event<>();
	public SimpleEvent changed = new SimpleEvent();
	private Runnable contentChangedListener = () -> { changed.fire(); };
	
	@Override
	public long getOffset() {
		return offset;
	}
	
	@Override
	public long getLength() {
		return length;
	}
	
	public List<ModelVariable<?>> getVariables() {
		return vars;
	}
	
	public ModelVariable<?> getVariable(String name) {
		for (ModelVariable<?> var : vars)
			if (name.equals(var.name))
				return var;
		if (parent == null)
			return null;
		return parent.getVariable(name);
	}
	
	public List<ModelBlock> getBlocks() {
		return blocks;
	}
	
	public ModelBlock getBlock(String name) {
		for (ModelBlock block : blocks)
			if (name.equals(block.name))
				return block;
		if (parent == null)
			return null;
		return parent.getBlock(name);
	}
	
	public List<IModelElement> getElements() {
		List<IModelElement> elements = new ArrayList<>(vars.size() + blocks.size());
		elements.addAll(vars);
		elements.addAll(blocks);
		return elements;
	}
	
	public List<IModelElement> getAllElements() {
		List<IModelElement> elements = new LinkedArrayList<>(25);
		getAllElements(elements);
		return elements;
	}
	
	private void getAllElements(List<IModelElement> list) {
		list.addAll(vars);
		list.addAll(blocks);
		for (ModelBlock b : blocks)
			b.getAllElements(list);
	}
	
	public void add(ModelVariable<?> var) {
		vars.add(var);
		variableAdded.fire(var);
		changed.fire();
		var.changed.addListener(contentChangedListener);
	}
	
	public void add(ModelBlock block) {
		blocks.add(block);
		blockAdded.fire(block);
		changed.fire();
		block.changed.addListener(contentChangedListener);
	}
	
	public void remove(ModelVariable<?> var) {
		if (!vars.remove(var)) return;
		variableRemoved.fire(var);
		changed.fire();
		var.changed.removeListener(contentChangedListener);
	}
	
	public void remove(ModelBlock block) {
		if (!blocks.remove(block)) return;
		blockRemoved.fire(block);
		changed.fire();
		block.changed.removeListener(contentChangedListener);
	}
	
}
