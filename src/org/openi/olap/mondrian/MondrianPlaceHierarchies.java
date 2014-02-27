package org.openi.olap.mondrian;

import java.util.ArrayList;
import java.util.List;

import mondrian.olap.Exp;
import mondrian.olap.Member;
import mondrian.olap.SchemaReader;
import mondrian.olap.Syntax;
import mondrian.mdx.UnresolvedFunCall;
import mondrian.mdx.MemberExpr;

import org.apache.log4j.Logger;

import com.tonbeller.jpivot.core.ExtensionSupport;
import com.tonbeller.jpivot.olap.model.Axis;
import com.tonbeller.jpivot.olap.model.Hierarchy;
import com.tonbeller.jpivot.olap.navi.PlaceHierarchiesOnAxes;
import com.tonbeller.jpivot.olap.query.Quax;

/**
 * generate Mondrian axis according to navigator
 */
public class MondrianPlaceHierarchies extends ExtensionSupport implements
		PlaceHierarchiesOnAxes {

	private boolean expandAllMember = false;
	ArrayList aMemberSet = null;
	static Logger logger = Logger.getLogger(MondrianPlaceHierarchies.class);

	/**
	 * Constructor sets ID
	 */
	public MondrianPlaceHierarchies() {
		super.setId(PlaceHierarchiesOnAxes.ID);
	}

	/**
	 * @see com.tonbeller.jpivot.olap.navi.PlaceHierarchiesOnAxes#createMemberExpression(Hierarchy)
	 */
	public Object createMemberExpression(Hierarchy hier) {

		mondrian.olap.Hierarchy monHier = ((MondrianHierarchy) hier)
				.getMonHierarchy();

		// if the query does not contain the hier,
		// just return the highest level
		MondrianModel model = (MondrianModel) getModel();
		MondrianQueryAdapter adapter = (MondrianQueryAdapter) model
				.getQueryAdapter();

		// find the Quax for this hier
		Quax quax = adapter.findQuax(hier.getDimension());
		if (quax == null) {
			// the hierarchy was not found on any axis

			SchemaReader scr = model.getSchemaReader().withLocus();

			return MondrianUtil.topLevelMembers(monHier, expandAllMember, scr);
			// return top level members of the hierarchy
		}

		// the member expression is the list of members plus the list of
		// FunCalls
		// for this dimension
		int iDimension = quax.dimIdx(hier.getDimension());
		return quax.genExpForDim(iDimension);
	}

	/**
	 * @see com.tonbeller.jpivot.olap.navi.PlaceHierarchiesOnAxes#setQueryAxis(Axis,
	 *      Object[])
	 */
	public void setQueryAxis(Axis target, Object[] memberExpressions) {

		MondrianModel model = (MondrianModel) getModel();
		MondrianQueryAdapter adapter = (MondrianQueryAdapter) model
				.getQueryAdapter();

		// locate the appropriate query axis
		int iQuax = ((MondrianAxis) target).getOrdinal();
		if (adapter.isSwapAxes())
			iQuax = (iQuax + 1) % 2;
		Quax quax = adapter.getQuaxes()[iQuax];

		int nDimension = 0;
		for (int i = 0; i < memberExpressions.length; i++) {
			if (memberExpressions[i] != null)
				++nDimension;
		}

		// if any of the member expressions is a memberlist from PlaceMembers
		// we will have to reset sorting
		Object[] sets = new Object[nDimension];
		boolean changedMemberSet = false;
		int j = 0;
		for (int i = 0; i < memberExpressions.length; i++) {
			// null possible due to access control
			if (memberExpressions[i] instanceof List) {
				List memberList = (List) memberExpressions[i];
				Exp[] members = new Exp[memberList.size()];
				for (int k = 0; k < members.length; k++) {
					members[k] = new MemberExpr((Member) memberList.get(k));
				}
				if (members.length == 1)
					sets[j++] = members[0];
				else
					sets[j++] = new UnresolvedFunCall("{}", Syntax.Braces,
							members);
				changedMemberSet = true;
			} else if (memberExpressions[i] != null) {
				// object generated by createMemberExpression or
				// CalcSet.createAxisExpression
				sets[j++] = memberExpressions[i];
			}
		}

		// generate the crossjoins
		quax.regeneratePosTree(sets, true);

		if (logger.isInfoEnabled()) {
			String changed = "";
			if (changedMemberSet)
				changed = " changed by navi";
			logger.info("setQueryAxis axis=" + quax.getOrdinal()
					+ " nDimension=" + nDimension + changed);
			logger.info("Expression for Axis=" + quax.toString());
		}

		// tell listeners, that the axis was changed.
		quax.changed(this, changedMemberSet);

		model.fireModelChanged();
	}

	/**
	 * @see PlaceHierarchiesOnAxes#setExpandAllMember
	 */
	public void setExpandAllMember(boolean expandAllMember) {
		this.expandAllMember = expandAllMember;
	}

	/**
	 * @see PlaceHierarchiesOnAxes#getExpandAllMember
	 */
	public boolean getExpandAllMember() {
		return expandAllMember;
	}

} // End MondrianPlaceHierarchies
