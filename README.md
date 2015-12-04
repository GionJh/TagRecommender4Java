<h1>TagRecommender4Java</h1>
<h3>A java library for Tag Recommendation</h3>

<pre>
Tag recommendation is the action of recommending new tags to be added to a
resource based on the tags the resource already has or
other information about the resource such as its title.

This java library does just that.

Tutorial

Recommender r = new Recommender(1); 

List<List<String>> list = new ArrayList<List<String();

List<String> tagSet1 = new ArrayList<String>();
tagSet1.add("Saturn");
tagSet1.add("Mars");

List<String> tagSet2 = new ArrayList<String>();
tagSet2.add("Venus");
tagSet2.add("Mars");

tagSet2.add("Earth");

list.add(tagSet1);
list.add(tagSet2);

r.computeRules(list);

List<String> myTags = new ArrayList<String>();
myTags.add("Mars");
myTags.add("Pluto");
List<Sting> suggested = r.suggestTags(myTags,null);

</pre>
