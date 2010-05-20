#!/usr/bin/env ruby

require 'sling/test'
require 'sling/search'
require 'test/unit.rb'
require 'test/unit/ui/console/testrunner.rb'
include SlingSearch

class TC_Kern270Test < SlingTest

  def test_modify_user_after_group_join
    m = "1a"+Time.now.to_i.to_s
	username = "testuser-#{m}"
	groupname = "g-testgroup2-#{m}"
    u = create_user(username)
    puts @s.get_node_props_json(SlingUsers::User.url_for(u.name))
    g = create_group(groupname)
    g.add_member(@s, u.name, "user")
    res = u.update_properties(@s, "fish" => "cat")
    assert_equal("200", res.code, "Expected modification to succeed")
  end

end

Test::Unit::UI::Console::TestRunner.run(TC_Kern270Test)

