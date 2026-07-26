/* Aquafish MySQL V18：前台站点说明与数据库驱动导航默认值。 */

INSERT IGNORE INTO `${tablePrefix}options`
    (`option_key`, `option_value`, `option_group`, `autoload`)
VALUES
    (
        'site.description',
        'CMS + 强论坛 + AI 内容社区',
        'site',
        1
    ),
    (
        'site.navigation',
        '[{"key":"home","label":"首页","url":"/site","location":"primary","target":"_self","visibility":"PUBLIC","enabled":true,"sortOrder":10},{"key":"content","label":"内容","url":"/content","location":"primary","target":"_self","visibility":"PUBLIC","enabled":true,"sortOrder":20},{"key":"forum","label":"论坛","url":"/forum","location":"primary","target":"_self","visibility":"PUBLIC","enabled":true,"sortOrder":30},{"key":"login","label":"登录","url":"/login","location":"account","target":"_self","visibility":"ANONYMOUS","enabled":true,"sortOrder":10},{"key":"register","label":"注册","url":"/register","location":"account","target":"_self","visibility":"ANONYMOUS","enabled":true,"sortOrder":20},{"key":"member","label":"个人中心","url":"/member","location":"account","target":"_self","visibility":"AUTHENTICATED","enabled":true,"sortOrder":30},{"key":"admin","label":"管理后台","url":"/admin","location":"account","target":"_self","visibility":"ADMIN","enabled":true,"sortOrder":40}]',
        'site',
        1
    );
