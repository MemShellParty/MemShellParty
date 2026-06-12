import jenkins.model.*
import hudson.security.*
import jenkins.security.s2m.AdminWhitelistRule

def instance = Jenkins.getInstance()

// 关闭登录认证
instance.disableSecurity()

// 关闭 CSRF 保护
instance.setCrumbIssuer(null)

instance.save()